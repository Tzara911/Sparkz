
MERGE INTO profiles (user_id, username, email, first_name, last_name, school_name, grade_year, account_status, onboarding_completed, time_zone)
KEY (user_id)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'demo_teacher', 'demo1@example.com', 'Dana', 'Teacher', 'Sparkz High', '12', 'active', false, 'America/Chicago'),
    ('00000000-0000-0000-0000-000000000002', 'demo_learner', 'demo2@example.com', 'Lee', 'Learner', 'Sparkz High', '11', 'active', false, 'America/Chicago');

MERGE INTO skills (skill_id, skill_name, description)
KEY (skill_id)
VALUES
    (1, 'Guitar', 'Acoustic and electric guitar basics'),
    (2, 'Spanish', 'Conversational Spanish'),
    (3, 'Python', 'Intro programming with Python'),
    (4, 'Chess', 'Chess strategy and tactics'),
    (5, 'Baking', 'Baking bread and pastries');

MERGE INTO user_skills (user_id, skill_id, skill_type)
KEY (user_id, skill_id, skill_type)
VALUES
    ('00000000-0000-0000-0000-000000000001', 1, 'teach'),
    ('00000000-0000-0000-0000-000000000002', 1, 'learn'),
    ('00000000-0000-0000-0000-000000000002', 3, 'learn');
