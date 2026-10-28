# Discovery and matching (Issue #7)

## Temporary test frontend

`discovery.html` and `matches.html` are temporary test pages for validating the
Java APIs and the #7 → #8 workflow. Their HTML/CSS/JavaScript is a testing aid;
the team's production frontend is still pending. Once it is ready, replace these
pages with the production frontend and connect it to the existing discovery and
match-request APIs, using the authentication contract below. The production design
does not need to follow these test pages.

## Local demo

With JDK 17 or later:

```sh
bash mvnw test
bash mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open http://localhost:8080/discovery.html. Use the same demo accounts as
[match requests](match-requests.md), password `local-demo`:

- `00000000-0000-0000-0000-000000000001` (Alex): teaches Java, learns Spanish; Monday/Wednesday.
- `00000000-0000-0000-0000-000000000002` (Sam): teaches Spanish, learns Java; Monday/Friday.

The other person appears with a mutual exchange badge and Monday in common. Filter
by their skills or public weekdays, choose a compatible skill, and send a request.
Use another browser profile/private session as the recipient to accept or decline
at `/matches.html`. The sender can cancel pending requests there. Refresh discovery
after another user acts. Pending/accepted pairs are hidden in both discovery views;
canceled/declined pairs can appear again. H2 resets on restart and never alters Supabase.

## API and ranking

Both endpoints require the same verified Supabase bearer token as #8; the viewer's
UUID comes from the token subject. The viewer must be active, onboarded, and attend
a configured supported school (`SPARKZ_SUPPORTED_SCHOOLS`).

`GET /api/discovery` accepts:

| Parameter | Contract |
| --- | --- |
| `mode` | `recommended` (default): at least one complementary skill; `browse`: also includes people without complementary skills |
| `teaches` | Optional positive skill ID the candidate teaches |
| `learns` | Optional positive skill ID the candidate wants to learn |
| `day` | Optional public weekday: 1 = Monday through 7 = Sunday |
| `page` | Zero-based, default 0, range 0–10000 |
| `size` | Default 12, range 1–50 |

Filters combine with AND. Unknown positive skill IDs return an empty page. Invalid
values return 400. Missing/invalid authentication returns 401 when JWT is configured;
missing issuer fails closed with 403. Missing viewer profile returns 404, and
ineligible viewer returns 422. Candidates must be active, onboarded, from the
viewer's exact school, and not the viewer. Either direction of a block excludes a
candidate. Existing `pending` or `accept` pairs are excluded in both directions,
regardless of skill, matching #8's duplicate rule.

Both modes sort by:

1. Reciprocal exchanges first: each person teaches at least one skill the other learns.
2. Number of complementary skill/direction matches, descending.
3. Number of shared public weekdays, descending.
4. Case-insensitive username, then UUID, for deterministic ties.

Days improve ranking but are not required for a match. An empty availability list
does not exclude an otherwise compatible person. Pagination uses an extra row to
calculate `hasNext`; skills and public days are fetched in batches for the current
page and viewer. Pages reflect current data, so changes between requests can shift
results; refresh after edits.

The response is `{profiles: [...], page, size, hasNext}`. Each profile contains only
`userId`, `username`, `bio`, `schoolName`, `teachingSkills`, `learningSkills`,
`availableDays`, `learnFromThem`, `teachThem`, `commonDays`, and `reciprocal`.
Skill entries are `{id, name}` using the existing `skills.skill_id`.
`learnFromThem` lists skills the candidate teaches and the viewer learns;
`teachThem` is the reverse. These arrays and `commonDays` explain the recommendation.

`GET /api/discovery/skills` returns the shared skill catalog for the two filters.
It applies the same viewer authentication and eligibility checks.

## Database contract and privacy

The implementation uses the schema/constraint reports supplied from Supabase:

| Table | Fields used |
| --- | --- |
| `profiles` | `user_id`, `username`, `bio`, `school_name`, `account_status`, `onboarding_completed` |
| `skills` | `skill_id`, `skill_name` |
| `user_skills` | `user_id`, `skill_id`, `skill_type` (`teach` / `learn`) |
| `profile_available_days` | `user_id`, `day_of_week` (1–7), composite primary key; user FK to `profiles` with cascade |
| `blocked_users` | `blocker_id`, `blocked_id` |
| `match_requests` | `sender_id`, `receiver_id`, `status` (`pending` / `accept` are active) |

The weekday check confirms the numeric range; it does not assign weekday names.
This API defines ISO weekday numbering (Monday = 1, Sunday = 7), which #6/#16/#17
must also use when saving availability. Confirm existing data follows that convention
before live integration; this change does not renumber stored values.

Discovery never queries `profile_available_slots`, email, phone, address, date of
birth, or other private profile fields. It uses an explicit public projection rather
than serializing database rows. User-provided names and biographies render as text.
General available days are hints, not detailed scheduling commitments.

No production DDL, RLS, or grants are changed. `db/local-schema.sql` is only a reduced
local/test fixture; normal startup does not initialize Supabase. The existing
trusted backend connection must be able to read these tables, and RLS/grants still
need their separate live integration review (#19). Local tests do not establish
live Supabase connectivity or browser-facing database permissions.

## Coordination with the other issues

- **#8 / PR #21:** this branch is based on `feature/8-match-requests`. Review its PR
  against that branch; merge #21 first, then retarget this PR to `main`. The page
  calls the existing `POST /api/match-requests` with `receiverId` and `skillId`.
  It does not create requests by writing directly to Supabase. Shared
  `MatchEligibility` keeps account/school validation consistent. #8 rechecks all
  eligibility and duplicate rules inside its existing transaction on every send.
  The page refreshes on successful sends and stale 404/409/422 responses.
- **#5:** provide `window.sparkzAuth.getAccessToken()` before page scripts execute;
  the shared `/sparkz-api.js` asks for the current token on each call, so the login
  implementation owns refresh. Both pages use the same helper. No tokens are stored
  by these pages. `/api/match-session` is only a local/test Basic-auth + CSRF fallback.
- **#6/#16/#17:** save `teach`/`learn`, exact configured school names, onboarding and
  account status, and ISO public weekdays using the established schema. Detailed
  slots stay outside discovery. Only `onboarding_completed` marks a profile complete.
- **#4:** the standalone page reuses #8's styles/navigation and can be placed in the
  shared layout when it lands; no shared layout exists on this base branch yet.
- **Blocking:** the existing table is checked in both directions immediately.

Automated coverage includes ranking, filter combinations, stable pagination, privacy,
ineligible/blocked users, both-direction active pairs, missing availability, invalid
queries, JWT protection, and the discovery → send → cancel/decline/accept flow. The
existing #8 tests run alongside these checks to catch regressions.
