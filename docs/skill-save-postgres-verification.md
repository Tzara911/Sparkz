# Skill-saving PostgreSQL verification

Verified November 11, 2026 (America/Chicago). This is an Issue #10 acceptance
check of the profile/skills functionality from #6. It verifies application/database
compatibility; it does not replace #19's live Supabase permissions/RLS review.

## Reproduction

Baseline: main commit `16f7ca7` (PR #30 merge). With the existing repository SQL
unchanged, run the new `skillSaveReadsBackAndRemainsIdempotentOnPostgres` test on a
fresh, disposable PostgreSQL 17 database. The request uses real local Basic
authentication and the CSRF-enabled Spring MVC pipeline:

```text
PUT /api/profiles/me/skills/1/learn
Expected: 204, with a persisted user/skill/type association
Actual: request processing failed with BadSqlGrammarException
Cause: PostgreSQL syntax error in MERGE INTO user_skills (...) KEY (...)
```

The before-fix run executed one test and reported one error. PostgreSQL does not
support H2's `MERGE ... KEY` syntax. The previous seven PostgreSQL tests covered
matching, availability and discovery, but did not call this skill-saving path.

## Fix

Use `INSERT ... ON CONFLICT DO NOTHING`, supported by PostgreSQL and H2's
PostgreSQL mode. The existing composite primary key `(user_id, skill_id,
skill_type)` makes duplicate handling atomic while preserving separate teach and
learn associations. No schema migration is required.

## Verified behavior

Four additional PostgreSQL regression tests exercise:

1. Add a skill through the API, read it back, repeat the add without duplicating
   it, and retain the same user's separate teach/learn roles.
2. Read and delete only the authenticated user's association. Another user's
   delete request for the same skill does not remove the owner's association.
3. Reject invalid roles, missing skills, unauthenticated writes and missing CSRF
   tokens without changing the database.
4. Process concurrent adds successfully while persisting one association.

After the fix, the full Maven suite passed: **100 tests, zero failures, zero
errors, zero skipped**, including **11 PostgreSQL checks**. Existing H2 tests
also passed. The disposable loopback-only PostgreSQL container was removed
following both the before-fix and after-fix runs. No shared Supabase instance
was accessed.

## Run again

Prepare a fresh local PostgreSQL database using the instructions in
[Real PostgreSQL validation](availability-discovery.md#real-postgresql-validation).
For the PostgreSQL checks only, run:

```sh
./mvnw -Dtest=PostgresMatchContractTests test
```

For the full suite, prepare another fresh database and run:

```sh
./mvnw test
```

The three `SPARKZ_POSTGRES_TEST_*` variables must be set as documented. Without
the opt-in URL, the PostgreSQL class is skipped. Use a fresh database for each
run because the fixture creates its own tables. Real Google login, deployed
Supabase configuration, and RLS remain separate integration checks.
