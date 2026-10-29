# Match requests (Issue #8)

`matches.html` is a temporary test page for backend integration testing. Once the
production frontend is ready, it will replace this page and connect to the existing
Java match-request API and authentication contract documented below.

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

Both accounts must have database account status `active`, be onboarded, and have the exact same school name in
`SPARKZ_SUPPORTED_SCHOOLS` (comma-separated allowlist). The **selected** skill must
have opposite `teach`/`learn` database roles between the users. Either teaching direction
is allowed. Reciprocal skills are a recommendation priority in #7, not a request
requirement. Blocks in either direction reject sends and acceptance. Acceptance
rechecks eligibility, but decline/cancel remain available after eligibility changes.

## Supabase integration checklist

The latest supplied Supabase SQL export defines the following column and value
contract. Earlier catalog reports provide additional constraint detail; this is
not a claim that live permissions or connectivity have been tested:

| Table | Columns read/written |
| --- | --- |
| profiles | `user_id uuid` references `auth.users(id)`, `account_status text` (`active` / `deactivated`, default `active`), `onboarding_completed boolean` (default `false`), `school_name text` (nullable) |
| skills | `skill_id bigint` |
| user_skills | `user_id uuid`, `skill_id bigint`, `skill_type text` (`teach` / `learn`); composite primary key on all three |
| match_requests | `request_id bigint` (identity), `sender_id uuid`, `receiver_id uuid`, `skill_id bigint`, `status text` (default `pending`), `created_at timestamptz` and `status_updated_at timestamptz` (default `now()`) |
| blocked_users | `blocker_id uuid`, `blocked_id uuid` |

The latest supplied SQL stores the accepted state as `accepted`. Earlier reports
used `accept`; the application now targets the latest contract. No migration is
run by the application. Java maps these stored values to the existing API/UI labels:

| API/UI label | Supabase value |
| --- | --- |
| `Pending` | `pending` |
| `Accepted` | `accepted` |
| `Declined` | `declined` |
| `Canceled` | `canceled` |

`MatchStatus` owns this conversion and `MatchStore` owns the database SQL. Duplicate
checks and the review-only partial index use `pending` and `accepted`. Normal API
responses continue to expose `Accepted`, so clients do not need a new status label.

`db/local-schema.sql` is a reduced local test fixture, **not** a Supabase migration.
It uses the supplied values/defaults and relevant checks/FKs. Unused profile fields
and the cross-schema `auth.users` foreign key are omitted; the demo skill identity
allows explicit seed IDs, while request IDs use GENERATED ALWAYS. No SQL
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

Existing indexes, RLS policies, backend grants, and live connectivity have not yet
been verified. Review `db/supabase-match-guards.sql` against existing indexes and
apply it manually after resolving pre-existing duplicate active pairs. The proposed partial unique index prevents
both same-direction and opposite-direction Pending/Accepted duplicates, across skills.
API transactions also lock profile rows in UUID order before checking/inserting, so
concurrent API sends serialize even in the H2 demo. Status transitions use a conditional
`UPDATE ... WHERE status = 'pending'` to ensure one winner under concurrency.

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
  `/discovery.html` now sends directly through that API; see [discovery](discovery.md).
  Both pages load `/sparkz-api.js` before their page script for shared authentication.
- **#4:** the standalone responsive page and styles can be placed inside the shared
  navigation when it lands. Currently the repository has no shared layout.
- **Blocked users:** validation is included because the supplied ERD has the table.
  The table must exist; the backend does not silently skip validation when absent.
- **Notifications:** not emitted here; event delivery belongs to the follow-up integration.

Tests cover persistence, incoming/outgoing isolation, all status actions, authorization,
validation, both-direction duplicates/blocks, ineligible accounts/schools/skills,
acceptance revalidation, and concurrent sends and terminal transitions. Schema
regression tests also read rows inserted with the database defaults, map existing
`accepted` rows, verify the stored values of each terminal transition, and confirm that
same-role skills cannot match. JWT HTTP tests additionally use locally signed RSA and EC tokens and a local JWKS
server to verify signature, issuer, audience, expiry, not-before, and UUID identity
validation. These tests validate the application against a fixture aligned with the
supplied schema reports. They do not prove live Supabase connectivity, RLS/grants,
or PostgreSQL partial-index behavior.


## SRS class diagram integration

The matching business entry point is `matching.MatchOperations`, with
`sendRequest`, `acceptRequest`, `declineRequest`, and `cancelRequest` operations.
The controller calls these named operations; eligibility checks, profile-row locks
and conditional status transitions remain inside their transactions.

The diagram's `user` argument is represented here by a verified UUID. Sending
also takes the recipient UUID and selected skill ID; the other actions take the
request ID so historical requests between the same users cannot be confused.
These application methods return a `MatchRequest` HTTP result rather than the
conceptual diagram's void. Exact User-typed domain signatures still depend on
#24's shared model; this is responsibility/operation alignment, not a claim that
all Java signatures already reproduce the diagram literally.

See [shared class/database contract](class-diagram-integration.md) for #24 mapping
and integration boundaries. The HTTP paths, request bodies and capitalized status
labels remain unchanged for the production frontend.
