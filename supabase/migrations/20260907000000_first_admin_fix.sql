-- Migration: 20260907000000_first_admin_fix.sql
-- Fix: the original handle_new_user() promoted the FIRST auth.users row to admin —
-- including anonymous users. The app signs in anonymously on first launch
-- (CloudAuth.ensureSignedIn()), so the very first install would steal the admin
-- role and the real owner signing up afterwards would wrongly become student.
-- New rule: the first NON-ANONYMOUS user becomes admin, and only while no admin
-- exists yet. Anonymous (guest) users are always students.

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare
  has_admin boolean;
  initial_role text;
  raw_name text;
begin
  select exists (
    select 1 from public.user_roles where role = 'admin'
  ) into has_admin;

  if not has_admin and coalesce(new.is_anonymous, false) = false then
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
