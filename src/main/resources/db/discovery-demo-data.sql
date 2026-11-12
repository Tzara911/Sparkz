-- OPTIONAL LOCAL H2 DEMO ONLY. Fictional users for the approved discovery layout.
-- Load explicitly AFTER db/local-data.sql; never use as a Supabase migration.
update profiles set username = 'Alex', first_name = 'Alex', time_zone = 'America/Chicago',
    bio = 'Happy to share what I know and learn something new.'
    where user_id = '00000000-0000-0000-0000-000000000001';
update profiles set username = 'Sam Rivera', first_name = 'Sam', time_zone = 'America/Chicago',
    bio = 'Learning Java. Happy to help with Spanish.'
    where user_id = '00000000-0000-0000-0000-000000000002';
insert into profiles(user_id, username, first_name, school_name, onboarding_completed, time_zone, bio) values
    ('00000000-0000-0000-0000-000000000003', 'Maya Chen', 'Maya', 'Demo University', true, 'America/Chicago', 'Let’s swap guitar chords for a little Python.'),
    ('00000000-0000-0000-0000-000000000004', 'Jordan Lee', 'Jordan', 'Demo University', true, 'America/Chicago', 'Good coffee, good design, better study buddies.');
insert into skills(skill_id, skill_name) values
    (2, 'Guitar'), (3, 'Python'), (4, 'Graphic design'), (5, 'Photography'), (6, 'Spanish');
insert into user_skills(user_id, skill_id, skill_type) values
    ('00000000-0000-0000-0000-000000000001', 3, 'teach'),
    ('00000000-0000-0000-0000-000000000001', 5, 'teach'),
    ('00000000-0000-0000-0000-000000000001', 2, 'learn'),
    ('00000000-0000-0000-0000-000000000002', 6, 'teach'),
    ('00000000-0000-0000-0000-000000000003', 2, 'teach'),
    ('00000000-0000-0000-0000-000000000003', 3, 'learn'),
    ('00000000-0000-0000-0000-000000000004', 4, 'teach'),
    ('00000000-0000-0000-0000-000000000004', 5, 'learn');
insert into profile_available_days(user_id, day_of_week) values
    ('00000000-0000-0000-0000-000000000001', 1),
    ('00000000-0000-0000-0000-000000000001', 3),
    ('00000000-0000-0000-0000-000000000001', 5),
    ('00000000-0000-0000-0000-000000000002', 1),
    ('00000000-0000-0000-0000-000000000002', 4),
    ('00000000-0000-0000-0000-000000000003', 1),
    ('00000000-0000-0000-0000-000000000003', 3),
    ('00000000-0000-0000-0000-000000000004', 2),
    ('00000000-0000-0000-0000-000000000004', 5);
