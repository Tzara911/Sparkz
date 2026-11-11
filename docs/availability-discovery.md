# Availability (#16) and discovery (#7)

## Status and dependencies

Implemented locally: authenticated availability management, public profile browsing,
filtered recommendations, and linking a compatible skill to the existing #8 match
request page. The shared page layout and final branding remain with #4/PR #29;
this browse page currently reuses the existing match page styles. #17's owner can
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
