insert into profiles(user_id, onboarding_completed, school_name) values
('00000000-0000-0000-0000-000000000001', true, 'Demo University'),
('00000000-0000-0000-0000-000000000002', true, 'Demo University');
insert into skills(skill_id, skill_name) values (1, 'Java');
insert into user_skills values
('00000000-0000-0000-0000-000000000001', 1, 'teach'),
('00000000-0000-0000-0000-000000000002', 1, 'learn');

update profiles set username = 'Alex', bio = 'Happy to help with Java. Hoping to learn Spanish.' where user_id = '00000000-0000-0000-0000-000000000001';
update profiles set username = 'Sam', bio = 'I teach Spanish and would love to learn Java.' where user_id = '00000000-0000-0000-0000-000000000002';
insert into skills(skill_id, skill_name) values (2, 'Spanish');
insert into user_skills values
('00000000-0000-0000-0000-000000000001', 2, 'learn'),
('00000000-0000-0000-0000-000000000002', 2, 'teach');
insert into profile_available_days values
('00000000-0000-0000-0000-000000000001', 1),
('00000000-0000-0000-0000-000000000001', 3),
('00000000-0000-0000-0000-000000000002', 1),
('00000000-0000-0000-0000-000000000002', 5);
