-- Used only with the isolated acceptance-test datasource. Never run against Supabase.
delete from match_requests;
delete from blocked_users;
delete from user_skills;
delete from profile_available_days;
delete from profiles;
delete from skills;

insert into profiles(user_id, username, email, first_name, last_name, school_name,
    bio, onboarding_completed, address, phone, dob, time_zone)
values
('00000000-0000-0000-0000-000000000001', 'alice', 'alice@example.test', 'Alice', 'One',
 'Demo University', 'Alice public bio', true, 'Private Alice address', '555-0101', '2000-01-01', 'America/Chicago'),
('00000000-0000-0000-0000-000000000002', 'bob', 'bob@example.test', 'Bob', 'Two',
 'Demo University', 'Bob public bio', true, 'Private Bob address', '555-0102', '2000-02-02', 'America/Chicago'),
('00000000-0000-0000-0000-000000000003', 'charlie', 'charlie@example.test', 'Charlie', 'Three',
 'Demo University', 'Charlie public bio', true, null, null, null, 'America/Chicago');

insert into skills(skill_id, skill_name, description) values
(4000000001, 'Java', 'Java programming'), (2, 'Spanish', null);
insert into user_skills(user_id, skill_id, skill_type) values
('00000000-0000-0000-0000-000000000001', 4000000001, 'teach'),
('00000000-0000-0000-0000-000000000001', 2, 'learn'),
('00000000-0000-0000-0000-000000000002', 4000000001, 'learn'),
('00000000-0000-0000-0000-000000000002', 2, 'teach');
insert into profile_available_days(user_id, day_of_week) values
('00000000-0000-0000-0000-000000000001', 1),
('00000000-0000-0000-0000-000000000001', 3),
('00000000-0000-0000-0000-000000000002', 1);
