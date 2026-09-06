-- Migration: 20260906000000_init_schema.sql
-- Description: Complete initial schema for Zimaster:
--              6 tables, 1 view, 14 RLS policies, 4 functions, GRANTs, Realtime publication

create extension if not exists "pgcrypto";

-- Drop existing objects if any (clean idempotent migration)
drop view if exists public.leaderboard cascade;
drop table if exists public.user_roles cascade;
drop table if exists public.user_progress cascade;
drop table if exists public.profiles cascade;
drop table if exists public.lessons cascade;
drop table if exists public.quotes cascade;
drop table if exists public.announcements cascade;

-- 1. Profiles table
create table public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  email text,
  display_name text not null default 'Learner',
  photo_url text,
  is_anonymous boolean not null default false,
  streak integer not null default 0,
  xp integer not null default 0,
  completed_lessons_count integer not null default 0,
  words_learned_count integer not null default 0,
  accuracy double precision not null default 0.0,
  last_active timestamptz default now(),
  last_active_millis bigint not null default 0,
  device_model text,
  android_version text,
  app_version text,
  platform text default 'android',
  role text not null default 'student',
  created_at timestamptz default now(),
  created_at_millis bigint not null default 0
);

-- 2. User Roles table (role checking & privilege escalation guard)
create table public.user_roles (
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('admin', 'student')),
  created_at timestamptz not null default now(),
  primary key (user_id, role)
);

-- 3. Security Definer Helper Functions
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.user_roles
    where user_id = auth.uid() and role = 'admin'
  );
$$;

create or replace function public.can_publish()
returns boolean language sql stable security definer set search_path = ''
as $$
  select public.is_admin();
$$;

create or replace function public.is_email_verified()
returns boolean language sql stable security definer set search_path = ''
as $$
  select coalesce(
    (select (email_confirmed_at is not null) from auth.users where id = auth.uid()),
    false
  );
$$;

-- 4. Trigger on auth.users for new users (first user is admin, others student)
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare
  user_count int;
  initial_role text;
  raw_name text;
begin
  select count(*) into user_count from auth.users;
  if user_count <= 1 then
    initial_role := 'admin';
  else
    initial_role := 'student';
  end if;

  insert into public.user_roles (user_id, role)
  values (new.id, initial_role)
  on conflict do nothing;

  raw_name := coalesce(
    new.raw_user_meta_data->>'full_name',
    new.raw_user_meta_data->>'name',
    new.raw_user_meta_data->>'display_name',
    'Learner'
  );

  insert into public.profiles (
    user_id,
    email,
    display_name,
    photo_url,
    is_anonymous,
    role,
    created_at,
    created_at_millis,
    last_active,
    last_active_millis
  ) values (
    new.id,
    new.email,
    raw_name,
    new.raw_user_meta_data->>'avatar_url',
    coalesce(new.is_anonymous, false),
    initial_role,
    now(),
    cast(extract(epoch from now()) * 1000 as bigint),
    now(),
    cast(extract(epoch from now()) * 1000 as bigint)
  )
  on conflict (user_id) do update set
    email = coalesce(excluded.email, public.profiles.email),
    display_name = coalesce(excluded.display_name, public.profiles.display_name);

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- 5. Lessons table
create table public.lessons (
  doc_id text primary key,
  course_id text not null,
  lesson_no integer not null,
  title text not null,
  level text not null,
  json text not null,
  updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint,
  updated_at_server timestamptz not null default now()
);

create index if not exists idx_lessons_course on public.lessons(course_id, lesson_no);
create index if not exists idx_lessons_updated_at on public.lessons(updated_at);

-- 6. User Progress table
create table public.user_progress (
  user_id uuid primary key references auth.users(id) on delete cascade,
  payload jsonb not null,
  updated_at timestamptz not null default now(),
  client_ts bigint not null default 0,
  constraint user_progress_size_limit check (octet_length(payload::text) <= 921600)
);

create or replace function public.push_progress(payload jsonb, client_ts bigint)
returns void language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;
  if octet_length(payload::text) > 921600 then
    raise exception 'State payload exceeds size limit of 921600 bytes';
  end if;
  insert into public.user_progress (user_id, payload, updated_at, client_ts)
  values (auth.uid(), payload, now(), client_ts)
  on conflict (user_id) do update set
    payload = excluded.payload,
    updated_at = now(),
    client_ts = excluded.client_ts;
end;
$$;

-- 7. Quotes table
create table public.quotes (
  id text primary key default gen_random_uuid()::text,
  text text not null check (char_length(text) between 1 and 599),
  author text not null default '',
  is_active boolean not null default true,
  created_at_millis bigint not null default (extract(epoch from now()) * 1000)::bigint,
  created_at timestamptz not null default now(),
  created_by_uid uuid references auth.users(id) on delete set null
);

create index if not exists idx_quotes_active on public.quotes(is_active);

-- 8. Announcements table
create table public.announcements (
  id text primary key default gen_random_uuid()::text,
  title text not null check (char_length(title) > 0),
  message text not null check (char_length(message) > 0),
  type text not null default 'info' check (type in ('info', 'update', 'challenge', 'alert')),
  created_at_millis bigint not null default (extract(epoch from now()) * 1000)::bigint,
  created_at timestamptz not null default now(),
  is_active boolean not null default true,
  is_probe boolean not null default false
);

create index if not exists idx_announcements_active_created on public.announcements(is_active, created_at_millis desc);

-- 9. Leaderboard View (public stats mirror, deliberately without email or role)
create or replace view public.leaderboard with (security_invoker = false) as
select
  user_id as uid,
  display_name,
  photo_url,
  streak,
  xp,
  completed_lessons_count,
  words_learned_count,
  accuracy,
  last_active_millis
from public.profiles;

-- 10. Enable Row Level Security (RLS) on all tables
alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;
alter table public.lessons enable row level security;
alter table public.user_progress enable row level security;
alter table public.quotes enable row level security;
alter table public.announcements enable row level security;

-- 11. RLS Policies (14 distinct policies)

-- Profiles (3 policies):
create policy "profiles_select_owner_or_admin"
  on public.profiles for select
  using (auth.uid() = user_id or public.is_admin());

create policy "profiles_insert_owner"
  on public.profiles for insert
  with check (auth.uid() = user_id);

create policy "profiles_update_owner"
  on public.profiles for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id and (public.is_admin() or role = (select p.role from public.profiles p where p.user_id = auth.uid())));

-- User Roles (1 policy - strictly SELECT only, no client writes):
create policy "user_roles_select"
  on public.user_roles for select
  using (auth.uid() = user_id or public.is_admin());

-- Lessons (3 policies):
create policy "lessons_select_authenticated"
  on public.lessons for select
  using (auth.role() = 'authenticated' or auth.role() = 'anon');

create policy "lessons_write_admin"
  on public.lessons for all
  using (public.is_admin())
  with check (public.is_admin());

-- User Progress (3 policies):
create policy "user_progress_select_owner"
  on public.user_progress for select
  using (auth.uid() = user_id);

create policy "user_progress_modify_owner"
  on public.user_progress for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- Quotes (2 policies):
create policy "quotes_select_all"
  on public.quotes for select
  using (auth.role() = 'authenticated' or auth.role() = 'anon');

create policy "quotes_modify_admin"
  on public.quotes for all
  using (public.is_admin())
  with check (public.is_admin());

-- Announcements (2 policies):
create policy "announcements_select"
  on public.announcements for select
  using (public.is_admin() or (is_active = true and is_probe = false));

create policy "announcements_modify_admin"
  on public.announcements for all
  using (public.is_admin())
  with check (public.is_admin());

-- 12. Realtime Publication
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'user_progress'
  ) then
    alter publication supabase_realtime add table public.user_progress;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'announcements'
  ) then
    alter publication supabase_realtime add table public.announcements;
  end if;
exception
  when undefined_object then null;
end $$;

-- 13. Explicit Permissions (GRANTs)
grant usage on schema public to anon, authenticated;
grant select on public.leaderboard to anon, authenticated;
grant select on public.lessons to anon, authenticated;
grant all on public.lessons to authenticated;
grant select on public.profiles to anon;
grant all on public.profiles to authenticated;
grant all on public.user_progress to authenticated;
grant select on public.quotes to anon;
grant all on public.quotes to authenticated;
grant select on public.announcements to anon;
grant all on public.announcements to authenticated;
grant select on public.user_roles to authenticated;

grant execute on function public.is_admin() to anon, authenticated;
grant execute on function public.can_publish() to anon, authenticated;
grant execute on function public.is_email_verified() to authenticated;
grant execute on function public.push_progress(jsonb, bigint) to authenticated;
