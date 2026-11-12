# Availability (#16) and discovery (#7)

## Status and dependencies

Implemented locally: authenticated availability management, public profile browsing,
filtered recommendations, and linking a compatible skill to the existing #8 match
request page. Discovery and match requests use the approved cream/orange/brown theme and shared
header/footer. These assets can be reused when #4/PR #29 is integrated. #17's owner can
use the API contract below without changing the availability data model.

Real Supabase login (#5), deployment integration, and live RLS/access-control
verification (#19) still require team integration. This work does not alter a live
database or run the Supabase schema export.

## Run locally

Use JDK 17 or newer with a compiler (a JRE alone is insufficient):

```sh
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open <http://127.0.0.1:8080/browse.html>. The local configuration is loopback-only
and uses H2, never Supabase. Sign in through browser Basic authentication:
`00000000-0000-0000-0000-000000000001` or
`00000000-0000-0000-0000-000000000002`; password `local-demo` by default.
The existing demo seed contains a Java teacher and learner, so the discovery
page can link directly to a compatible request. Local password is overridable
with `sparkz.local.password`.

Production pages use the existing #8 integration hook:
`window.sparkzAuth.getAccessToken()` must return a current verified Supabase
access token, supplied by #5. No tokens or credentials are stored by this page.
Local mutations require the CSRF token returned by `/api/match-session`.
Production API routes accept verified Bearer tokens, fail closed without an
issuer, and use the authenticated subject as the user ID.

## Availability API for #17

Days use ISO weekdays: 1 = Monday, 7 = Sunday. Times use `HH:mm[:ss]` in the
profile's IANA `timeZone`; they are recurring wall-clock times, not UTC instants.
The caller should set their profile time zone before entering local times. An
unset time zone is returned as null. Cross-midnight windows must be split across
two days. The API accepts multiple windows on a day; overlapping windows are not
merged or rejected. A day must be selected before adding a window.

| Method | Path | Body / result |
| --- | --- | --- |
| GET | `/api/profiles/me/availability` | `{days:[1,3],slots:[{slotId,dayOfWeek,startTime,endTime}],timeZone}` |
| PUT | `/api/profiles/me/availability/days` | `{days:[1,3]}`; returns updated availability |
| POST | `/api/profiles/me/availability/slots` | `{dayOfWeek:1,startTime:"09:00",endTime:"10:00"}`; returns created slot (201) |
| PUT | `/api/profiles/me/availability/slots/{slotId}` | Same slot body; returns updated slot |
| DELETE | `/api/profiles/me/availability/slots/{slotId}` | Deletes own slot (204) |

Replacing days preserves all slots on retained days and deletes slots on removed
days in the same transaction. **#17 should explain this before removing a day.**
An empty day list clears all availability. Repeated day values are deduplicated.
All writes lock the profile to serialize same-user updates. Days, slots, and
profile checks share the transaction. Unknown/non-owned slots return 404.
Invalid/missing weekdays or times and start >= end return 400. Inactive users
cannot manage availability. There is no public detailed-slot endpoint.

Public weekdays are available on `GET /api/discovery/{userId}` after the same
eligibility checks as browsing. No private slot data appears in discovery.

## Discovery API

`GET /api/discovery` returns `{profiles,total,offset,limit}`. Query parameters:

- `skillId`: optional positive integer; matches the candidate's skill.
- `skillType`: optional `teach` or `learn`; refers to the candidate's role.
- `day`: optional ISO weekday. Combined filters must all match.
- `recommended`: default false; true excludes candidates without a compatible skill.
- `offset`: default 0, minimum 0; `limit`: default 20, range 1–100.

`GET /api/discovery/{userId}` returns one public profile, or 404 if ineligible,
blocked in either direction, or absent. A viewer must have an active, onboarded
profile in a configured supported school. Candidates must be active, onboarded,
from the same school, and not the viewer. With no supported schools configured,
discovery is denied.

The public DTO explicitly includes userId, username, firstName, schoolName, bio,
avatarUrl, skill IDs/names/types, availableDays, canLearn, canTeach, commonDays,
reciprocal, reasons, and matchStatus (pending/accepted/null). It omits email,
address, phone, DOB, and all private slot records. `canLearn` lists skills the
candidate teaches that the viewer wants; `canTeach` lists skills the candidate
wants that the viewer teaches. Availability is weekday-level relevance, not a
promise of overlapping actual times, particularly across time zones.

Ordering is reciprocal matches first, then number of compatible teaching/learning
skills, then number of shared weekdays, with user ID as a stable tie-breaker.
MVP implementation fetches eligible public rows and bulk-loads skills/days before
ranking and paginating in memory. It avoids per-profile queries; large-scale
usage will need database-side ranking/pagination.

A card's Connect link sends `receiverId` and a compatible `skillId` to
`/matches.html`. The existing #8 form submits the request and enforces eligibility
again. Pending/accepted matches are labeled instead of offering duplicate links.
The server remains authoritative if eligibility changes after the page loads.

## Database contract

Column names follow the team-provided Supabase export. The export represents the
slot foreign key as two entries bearing the same constraint name; do not execute
that rendering as DDL. The logical parent is the composite
`(user_id,day_of_week)` key of `profile_available_days`. Local fixtures use that
composite FK plus `start_time < end_time`. No production migration is applied.
Live constraint verification and direct-Supabase slot privacy belong to #19;
Java route authorization does not replace RLS for clients accessing Supabase.

Tests use independent H2 databases per Spring context. The older profile/skill
tests have a dedicated fixture for their five-skill demo, while match/discovery
use the existing match fixture. This avoids conflicting fixture expectations.


## Approved visual direction

Discovery implements the last approved concept: centered “Find your people.” and
handwritten “Share your spark.”, a single description, no hero illustration, no
“Learn a little. Share a little.” eyebrow, and no “A little give. A little grow.”
section heading. The results count and profile cards are driven by the API.
Desktop uses three columns, tablet two, and phone one. Skill matches are selected
by default; users can still choose everyone eligible. Multiple compatible skills
produce a skill selector rather than silently choosing a request on the user's
behalf. The My profile control opens a read-only overview of the signed-in user;
profile editing remains a separate feature.

`sparkz-theme.css` contains the cream, orange, brown, border and surface tokens.
`sparkz-shell.js` renders the shared navigation/footer. Body font DM Sans and
handwritten font Yellowtail are self-hosted, with licenses in `static/fonts`.
`static/images/sparkz-logo.png` is a transparent rendition extracted with image
generation from the approved concept, not the team's original vector master.
Replace that one file with the official asset when available; no layout rewrite
is required. No uploaded screenshot is used as the page background.

For a local demo with the three fictional profile cards, opt into the additional
H2-only seed (this does not change the normal seed or test fixture):

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--spring.sql.init.data-locations=classpath:db/local-data.sql,classpath:db/discovery-demo-data.sql
```

Use demo account ending `0001` to see Maya, Sam and Jordan. Avatars reflect profile
initials, and all filters and Connect links use the running backend. The extra
fictional accounts are discovery candidates only, not additional Basic logins.

## Real PostgreSQL validation

The opt-in `PostgresMatchContractTests` require a **disposable local database**.
They create an `auth` stub schema, initialize application tables, delete fixture
rows, and reset identity sequences. They must not run against the team's shared
Supabase database. The URL gate accepts only
`jdbc:postgresql://127.0.0.1:<port>/sparkz_test`.

With Docker available, prepare a temporary PostgreSQL instance:

```sh
export SPARKZ_POSTGRES_TEST_URL=jdbc:postgresql://127.0.0.1:55432/sparkz_test
export SPARKZ_POSTGRES_TEST_USERNAME=postgres
export SPARKZ_POSTGRES_TEST_PASSWORD="$(openssl rand -hex 24)"
POSTGRES_PASSWORD="$SPARKZ_POSTGRES_TEST_PASSWORD" docker run --rm -d \
  --name sparkz-postgres-test -p 127.0.0.1:55432:5432 \
  --tmpfs /var/lib/postgresql/data:rw \
  -e POSTGRES_DB=sparkz_test -e POSTGRES_PASSWORD postgres:17-alpine
```

Check `docker exec sparkz-postgres-test pg_isready -h 127.0.0.1 -U postgres -d sparkz_test`
until ready, then run `./mvnw test`. Afterwards, run
`docker stop sparkz-postgres-test` and unset the three `SPARKZ_POSTGRES_TEST_*`
variables. A fresh container/database is needed for each suite run because the
schema initialization deliberately creates new tables. No database password is
stored in the repository.

The PostgreSQL suite verifies match defaults/status values, bigint IDs, foreign
keys, opposite-direction sends and competing transitions, plus recurring time
round-trips, private slot ownership, composite day/slot relationships, reciprocal
discovery, public-data filtering and bidirectional block exclusion. These checks
validate the real PostgreSQL engine using the supplied schema contract; they do
not certify live Supabase login, deployed schema differences, grants, or RLS.

Verified on PostgreSQL 17: the full Maven suite passed with 100 tests, zero
failures/errors/skips, including all eleven PostgreSQL contract checks. The four
additional skill-saving checks and the H2/PostgreSQL SQL compatibility fix are
documented in [the skill-saving verification report](skill-save-postgres-verification.md). The
disposable container was stopped and removed after verification.
