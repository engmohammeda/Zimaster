-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: 20260907000000_harden_rls_realtime_and_admin.sql
--
-- Hardening pass over the initial schema. Every statement is idempotent
-- (drop-if-exists before create) so this file is safe to apply on top of the
-- initial migration whether or not it has already run on the remote project.
--
-- Fixes, in order of importance:
--
--   1. ADMIN ESCALATION HOLE (security). `handle_new_user()` promoted the very
--      first row in auth.users to admin. The app signs every learner in
--      ANONYMOUSLY on first launch (CloudAuth.ensureSignedIn), so the first
--      *device to open the app* — potentially a stranger — would have become
--      admin of the whole platform: able to publish lessons, post global
--      announcements and read every user's email. Admin is now only ever
--      granted to a real (non-anonymous) account, and an anonymous identity
--      that is later upgraded to Google/email is promoted at that moment if the
--      project still has no admin.
--
--   2. REALTIME FILTERING (correctness). CloudSync.subscribeToRealtimeProgress
--      subscribes to postgres_changes on public.user_progress with
--      `filter("user_id", EQ, uid)`. Supabase Realtime can only evaluate a
--      filter on UPDATE/DELETE events when the table publishes its old record,
--      i.e. when REPLICA IDENTITY is FULL. Without it the subscription joins
--      successfully and then silently never fires.
--
--   3. LEAST-PRIVILEGE POLICIES. `lessons_write_admin` used `FOR ALL`, which
--      bundles INSERT/UPDATE/DELETE into one opaque rule. Split into three
--      explicit per-command policies, matching the Supabase guidance and making
--      the admin surface auditable at a glance.
--
--   4. MISSING DELETE POLICY ON profiles. profiles had SELECT/INSERT/UPDATE but
--      no DELETE policy at all, so a learner could never remove their own
--      profile row (account/data deletion). Added, owner-only.
--
--   5. NULL-SAFE ROLE GUARD. `profiles_update_owner`'s WITH CHECK compared
--      `role = (select p.role ...)`, which evaluates to NULL — and therefore
--      DENIES the write — whenever the profile row does not exist yet. Wrapped
--      in coalesce().
--
--   6. VIEW OWNERSHIP + INDEXES. Pin the leaderboard view to `postgres` (the
--      role that bypasses RLS, which is what makes a global leaderboard over an
--      RLS-protected table possible) and add the two indexes the app's hot
--      read paths need.
-- ═══════════════════════════════════════════════════════════════════════════

-- ───────────────────────────────────────────────────────────────────────────
-- 1. Admin escalation: only real, non-anonymous accounts can become admin
-- ───────────────────────────────────────────────────────────────────────────

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare
  initial_role text;
  raw_name text;
begin
  -- An anonymous session is a throwaway identity created automatically on app
  -- launch; it must never carry admin rights. A real account becomes admin only
  -- while the project still has no admin at all (i.e. the owner setting up).
  if coalesce(new.is_anonymous, false) then
    initial_role := 'student';
  elsif not exists (select 1 from public.user_roles where role = 'admin') then
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
    user_id, email, display_name, photo_url, is_anonymous, role,
    created_at, created_at_millis, last_active, last_active_millis
  ) values (
    new.id, new.email, raw_name, new.raw_user_meta_data->>'avatar_url',
    coalesce(new.is_anonymous, false), initial_role,
    now(), cast(extract(epoch from now()) * 1000 as bigint),
    now(), cast(extract(epoch from now()) * 1000 as bigint)
  )
  on conflict (user_id) do update set
    email = coalesce(excluded.email, public.profiles.email),
    display_name = coalesce(excluded.display_name, public.profiles.display_name),
    photo_url = coalesce(excluded.photo_url, public.profiles.photo_url),
    is_anonymous = excluded.is_anonymous;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Anonymous → permanent upgrade (Google / email linked onto an anonymous
-- session). Supabase updates the existing auth.users row rather than inserting
-- a new one, so the INSERT trigger above never fires for the upgrade. This
-- trigger mirrors the new identity onto public.profiles and promotes the user
-- to admin if — and only if — the project still has no admin.
create or replace function public.handle_user_upgraded()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare
  became_real boolean;
begin
  became_real := coalesce(old.is_anonymous, false) and not coalesce(new.is_anonymous, false);

  update public.profiles set
    email = coalesce(new.email, public.profiles.email),
    is_anonymous = coalesce(new.is_anonymous, false),
    photo_url = coalesce(new.raw_user_meta_data->>'avatar_url', public.profiles.photo_url),
    display_name = coalesce(
      new.raw_user_meta_data->>'full_name',
      new.raw_user_meta_data->>'name',
      new.raw_user_meta_data->>'display_name',
      public.profiles.display_name
    )
  where user_id = new.id;

  if became_real and not exists (select 1 from public.user_roles where role = 'admin') then
    insert into public.user_roles (user_id, role)
    values (new.id, 'admin')
    on conflict do nothing;
    update public.profiles set role = 'admin' where user_id = new.id;
  end if;

  return new;
end;
$$;

drop trigger if exists on_auth_user_upgraded on auth.users;
create trigger on_auth_user_upgraded
  after update of is_anonymous, email_confirmed_at, email on auth.users
  for each row execute function public.handle_user_upgraded();

-- ───────────────────────────────────────────────────────────────────────────
-- 2. Realtime: publish the old record so filtered UPDATE/DELETE events work
-- ───────────────────────────────────────────────────────────────────────────

alter table public.user_progress replica identity full;
alter table public.announcements replica identity full;

-- ───────────────────────────────────────────────────────────────────────────
-- 3. Lessons: replace the coarse FOR ALL policy with explicit per-command ones
-- ───────────────────────────────────────────────────────────────────────────

drop policy if exists "lessons_write_admin" on public.lessons;

drop policy if exists "lessons_insert_admin" on public.lessons;
create policy "lessons_insert_admin"
  on public.lessons for insert
  with check (public.is_admin());

drop policy if exists "lessons_update_admin" on public.lessons;
create policy "lessons_update_admin"
  on public.lessons for update
  using (public.is_admin())
  with check (public.is_admin());

drop policy if exists "lessons_delete_admin" on public.lessons;
create policy "lessons_delete_admin"
  on public.lessons for delete
  using (public.is_admin());

-- ───────────────────────────────────────────────────────────────────────────
-- 4. Profiles: allow a learner to delete their own profile row
-- ───────────────────────────────────────────────────────────────────────────

drop policy if exists "profiles_delete_owner" on public.profiles;
create policy "profiles_delete_owner"
  on public.profiles for delete
  using (auth.uid() = user_id);

-- ───────────────────────────────────────────────────────────────────────────
-- 5. Profiles update guard: make the role comparison NULL-safe
-- ───────────────────────────────────────────────────────────────────────────

drop policy if exists "profiles_update_owner" on public.profiles;
create policy "profiles_update_owner"
  on public.profiles for update
  using (auth.uid() = user_id)
  with check (
    auth.uid() = user_id
    and (
      public.is_admin()
      or role = coalesce(
        (select p.role from public.profiles p where p.user_id = auth.uid()),
        'student'
      )
    )
  );

-- ───────────────────────────────────────────────────────────────────────────
-- 6. Leaderboard view ownership + hot-path indexes
-- ───────────────────────────────────────────────────────────────────────────

-- security_invoker = false (set in the initial migration) means the view runs
-- with its owner's privileges. Pinning the owner to postgres — the role that
-- bypasses RLS on Supabase — is what makes a *global* leaderboard readable by
-- every learner while public.profiles itself stays owner/admin-only.
alter view public.leaderboard owner to postgres;
grant select on public.leaderboard to anon, authenticated;

-- CloudSync.fetchAllUsers orders the admin user list by last activity.
create index if not exists idx_profiles_last_active_millis
  on public.profiles (last_active_millis desc);

-- CloudSync.fetchLeaderboard orders by XP.
create index if not exists idx_profiles_xp
  on public.profiles (xp desc);

-- Role lookups (`select 1 from user_roles where role = 'admin'`) run inside
-- every is_admin() call, i.e. on nearly every request.
create index if not exists idx_user_roles_role
  on public.user_roles (role);
