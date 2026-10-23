# Match requests (Issue #8)

## Run locally without Supabase

Requires a **JDK** (not only a JRE), version 17 or later.

```sh
bash mvnw test
bash mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open http://localhost:8080/matches.html. The local server binds to 127.0.0.1,
uses an in-memory H2 database, and resets when stopped. It does not change Supabase.
When prompted, sign in with one of these demo usernames, password `local-demo`:

- `00000000-0000-0000-0000-000000000001`: teaches Java (skill ID `1`).
- `00000000-0000-0000-0000-000000000002`: learns Java.

Use separate browser profiles/private sessions for the two users. Send a request to
the other user's ID with skill ID `1`, then accept or decline from the other browser.
Pending outgoing requests can be canceled. Refresh to see changes from the other user.
Local Basic authentication retains CSRF protection. Set `sparkz.local.password` to
change the demo password. Never enable the `local` or `test` profile on a shared server.

## HTTP contract

All production requests require `Authorization: Bearer <Supabase access token>`.
The sender/actor is always the verified token's UUID subject, never a body parameter.

| Method | Path | Body / behavior |
| --- | --- | --- |
| POST | `/api/match-requests` | `{"receiverId":"<uuid>","skillId":1}`; returns 201 |
| GET | `/api/match-requests?direction=incoming` | Requests received by the current user |
| GET | `/api/match-requests?direction=outgoing` | Requests sent by the current user |
| PATCH | `/api/match-requests/{id}` | `{"status":"Accepted"}`, `Declined`, or `Canceled` |

Responses contain `id` (bigint), `senderId`, `receiverId`, `skillId`, `status`,
`createdAt`, and `statusUpdatedAt`. Lists include terminal statuses, newest first.
Only recipients accept/decline; only senders cancel; only pending requests change.
Nonparticipants get 404. Invalid input gets 400; unauthorized actors get 403;
duplicates and stale transitions get 409; ineligible pairs get 422.

Both accounts must be `Active`, onboarded, and have the exact same school name in
`SPARKZ_SUPPORTED_SCHOOLS` (comma-separated allowlist). The **selected** skill must
have opposite Teaching/Learning roles between the users. Either teaching direction
is allowed. Reciprocal skills are a recommendation priority in #7, not a request
requirement. Blocks in either direction reject sends and acceptance. Acceptance
rechecks eligibility, but decline/cancel remain available after eligibility changes.

## Supabase integration checklist

The supplied ERD confirms relationships and bigint/UUID types, but truncates column
names and does not show defaults or check constraints. The implementation uses this
explicit contract; compare it with the actual DDL before production integration:

| Table | Columns read/written |
| --- | --- |
| profiles | `user_id uuid`, `account_status text` (`Active`), `onboarding_completed boolean`, `school_name text` |
| skills | `skill_id bigint` |
| user_skills | `user_id uuid`, `skill_id bigint`, `skill_type text` (`Teaching` / `Learning`) |
| match_requests | `request_id bigint` (database-generated), `sender_id uuid`, `receiver_id uuid`, `skill_id bigint`, `status text`, `created_at timestamptz`, `status_updated_at timestamptz` |
| blocked_users | `blocker_id uuid`, `blocked_id uuid` |

Database mapping SQL is isolated in `MatchStore`. Align any differing column names,
status capitalization, identity defaults, or skill role values there and in fixtures.
`db/local-schema.sql` is a local test fixture, **not** a Supabase migration. No SQL
initialization runs under the normal application profile.

To export the missing DDL details, run `db/inspect-match-schema.sql` in Supabase
SQL Editor. It only reads column definitions, constraints, indexes, and RLS policies;
it does not read user records or change the database.

Set the existing database environment variables and:

```text
SUPABASE_AUTH_ISSUER=https://<project-ref>.supabase.co/auth/v1
SPARKZ_SUPPORTED_SCHOOLS=<exact supported school name>,<another school>
```

`SUPABASE_DB_PASSWORD` is the preferred database password variable. The application
also accepts `SUPABASE_BD_PASSWORD` as a fallback for existing environment bindings.

The backend verifies issuer, RS256/ES256 signature, required expiry, not-before,
UUID subject, and the `authenticated` audience using public signing keys from
`<issuer>/.well-known/jwks.json`. An OIDC discovery document is not required.
This requires Supabase asymmetric JWT signing keys and reachable JWKS. Legacy HS256 projects need an
explicit trusted decoder integration with #5; do not merely decode tokens. Missing
issuer configuration denies match API access; an empty school allowlist rejects sends.

Review `db/supabase-match-guards.sql` against the actual schema and apply it manually
after resolving pre-existing duplicate active pairs. The partial unique index prevents
both same-direction and opposite-direction Pending/Accepted duplicates, across skills.
API transactions also lock profile rows in UUID order before checking/inserting, so
concurrent API sends serialize even in the H2 demo. Status transitions use a conditional
`UPDATE ... WHERE status = 'Pending'` to ensure one winner under concurrency.

Use a trusted backend database identity. Audit Supabase RLS and grants so browser clients
cannot bypass backend rules with direct writes. Profile/skill/block writers should use
the same profile-row locking convention if eligibility edits must serialize strictly
with sends/acceptance. This change does not modify live RLS or live tables.

## Integration with other issues

- **#5:** connect the existing Supabase session by defining
  `window.sparkzAuth.getAccessToken()` before `/matches.js` runs. Return the current
  token (including refresh handling). The page keeps no tokens in local storage.
  `/api/match-session` exists only for the local/test demo.
- **#7:** send to the POST API from a profile, or link to
  `/matches.html?receiverId=<uuid>&skillId=<id>` to prefill the form.
- **#4:** the standalone responsive page and styles can be placed inside the shared
  navigation when it lands. Currently the repository has no shared layout.
- **Blocked users:** validation is included because the supplied ERD has the table.
  The table must exist; the backend does not silently skip validation when absent.
- **Notifications:** not emitted here; event delivery belongs to the follow-up integration.

Tests cover persistence, incoming/outgoing isolation, all status actions, authorization,
validation, both-direction duplicates/blocks, ineligible accounts/schools/skills,
acceptance revalidation, and concurrent sends and terminal transitions. JWT HTTP tests additionally use locally signed RSA and EC tokens and a local JWKS
server to verify signature, issuer, audience, expiry, not-before, and UUID identity
validation. H2 tests do not
prove compatibility with unseen Supabase DDL or PostgreSQL partial-index behavior.
