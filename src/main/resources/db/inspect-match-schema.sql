-- Read-only schema report for Supabase SQL Editor. Contains no user records.
-- Share these results to confirm the truncated ERD's exact column names and values.
select table_name, column_name, data_type, is_nullable, column_default, is_identity
from information_schema.columns
where table_schema = 'public'
  and table_name in ('profiles', 'skills', 'user_skills', 'match_requests', 'blocked_users')
order by table_name, ordinal_position;

select c.relname as table_name, con.conname as constraint_name,
       pg_get_constraintdef(con.oid) as definition
from pg_constraint con
join pg_class c on c.oid = con.conrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('profiles', 'skills', 'user_skills', 'match_requests', 'blocked_users')
order by c.relname, con.conname;

select tablename, indexname, indexdef
from pg_indexes
where schemaname = 'public' and tablename = 'match_requests'
order by indexname;

select c.relname as table_name, c.relrowsecurity as rls_enabled,
       c.relforcerowsecurity as rls_forced
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('profiles', 'user_skills', 'match_requests', 'blocked_users');

select tablename, policyname, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public'
  and tablename in ('profiles', 'user_skills', 'match_requests', 'blocked_users')
order by tablename, policyname;
