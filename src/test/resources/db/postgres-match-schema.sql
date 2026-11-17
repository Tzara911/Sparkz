-- Disposable PostgreSQL TEST database only. Not an application migration.
-- Column/value contract from the latest team SQL; Auth users below are test stubs.
create schema auth;
create table auth.users (id uuid primary key);
create table public.profiles (
    user_id uuid primary key references auth.users(id),
    username text unique,
    email text,
    first_name text,
    last_name text,
    school_name text,
    grade_year text,
    address text,
    phone text,
    dob date,
    created_at timestamptz default now(),
    time_zone text,
    account_status text not null default 'active' check (account_status in ('active', 'deactivated')),
    bio text,
    avatar_url text,
    onboarding_completed boolean not null default false,
    updated_at timestamptz not null default now()
);
create table public.skills (
    skill_id bigint generated always as identity primary key,
    skill_name text not null check (length(btrim(skill_name)) > 0),
    description text
);
create table public.user_skills (
    user_id uuid not null references public.profiles(user_id),
    skill_id bigint not null references public.skills(skill_id),
    skill_type text not null check (skill_type in ('teach', 'learn')),
    primary key (user_id, skill_id, skill_type)
);
create table public.match_requests (
    request_id bigint generated always as identity primary key,
    sender_id uuid not null references public.profiles(user_id),
    receiver_id uuid not null references public.profiles(user_id),
    skill_id bigint not null references public.skills(skill_id),
    status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'canceled')),
    created_at timestamptz not null default now(),
    status_updated_at timestamptz not null default now(),
    check (sender_id <> receiver_id)
);
create table public.blocked_users (
    blocker_id uuid not null references public.profiles(user_id),
    blocked_id uuid not null references public.profiles(user_id),
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);
create table public.learning_sessions (
    session_id bigint generated always as identity primary key,
    match_request_id bigint not null references public.match_requests(request_id),
    proposed_by uuid not null,
    teacher_id uuid not null references public.profiles(user_id),
    learner_id uuid not null references public.profiles(user_id),
    scheduled_start timestamptz not null,
    scheduled_end timestamptz not null,
    mode text not null check (mode in ('online', 'in-person')),
    location text,
    meeting_url text,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'canceled', 'declined')),
    created_at timestamptz not null default now(),
    message text
);
create table public.profile_available_days (
    user_id uuid not null references public.profiles(user_id),
    day_of_week smallint not null check (day_of_week between 1 and 7),
    primary key (user_id, day_of_week)
);
create table public.profile_available_slots (
    slot_id bigint generated always as identity primary key,
    user_id uuid not null,
    day_of_week smallint not null,
    start_time time not null,
    end_time time not null,
    foreign key (user_id, day_of_week) references public.profile_available_days(user_id, day_of_week),
    check (start_time < end_time)
);
insert into auth.users values
    ('00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000002');
insert into public.profiles(user_id, username, onboarding_completed, school_name) values
    ('00000000-0000-0000-0000-000000000001', 'Alex', true, 'Demo University'),
    ('00000000-0000-0000-0000-000000000002', 'Sam', true, 'Demo University');
insert into public.skills(skill_name, description) values ('Java', 'Java programming');
