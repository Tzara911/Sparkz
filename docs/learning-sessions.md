# Learning session scheduling (#18)

The session page and API let users manually propose and manage learning sessions
after an accepted match. Storage follows the existing `learning_sessions` table
and supplied Supabase column/value contract. The page uses the approved shared
brand theme. Automatic time suggestions remain separate work.

## Use the session page

Open `/sessions.html`, use Sessions in the feature navigation, or select
**Schedule a session** on an accepted incoming/outgoing match request. The accepted
match is preselected from the URL when available. Partner names, matched skills,
and compatible teaching/learning roles come from the existing authenticated
profile, matching, skills, and discovery APIs; no user IDs need to be entered.

Choose a start/end time and online or in-person mode. Online sessions require a
meeting URL; in-person sessions require a location. Inputs and displayed session
times use the browser/device time zone, shown beside the fields. The page sends
UTC instants, previews the local zone/offset name, and rejects nonexistent local
times skipped by daylight saving. Profile time zones do not silently override
the device's date input semantics.

The list supports all/incoming/outgoing directions, refresh, and pagination.
Recipients see Accept/Decline for future pending proposals; participants can
cancel pending/accepted future sessions. Decline and cancel have confirmation
dialogs. Completed terminal transitions remove buttons; stale transitions refresh
the list after a 409 response. API errors retain form input and re-enable controls.
Empty, loading, sign-in, no-accepted-match, and retry states are included.

Production uses `window.sparkzAuth.getAccessToken()` from #5 for fresh Bearer
headers. The local demo reuses `/api/match-session` and CSRF cookies. Public page
assets are allowed in the local/test filter; session data still requires auth.

## Try the complete flow locally

Start the isolated, loopback-only H2 demo with JDK 17 or newer:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

In another terminal, run the standard-library HTTP demo:

```sh
python3 scripts/demo_learning_sessions.py
```

The script uses the existing two demo accounts (UUIDs ending `0001` and `0002`,
password `local-demo`), cookies, and CSRF tokens. It creates or reuses their Java
match, accepts it, proposes an online session, accepts and cancels it, then
proposes and declines an in-person session. It can be rerun. Set
`SPARKZ_LOCAL_PASSWORD` if you changed the local demo password. Use
`--base-url http://127.0.0.1:<port>` for another local port. It accepts only local
HTTP URLs; the H2 fixture resets when the server stops. The meeting URL is a
fictional value and no meeting provider is contacted.

## API contract

Production calls use the existing Supabase Bearer authentication. The verified
subject supplies the actor UUID; callers cannot set `proposedBy`, `learnerId`, or
the initial status. Local Basic writes require the CSRF token from
`GET /api/match-session` with the same cookie jar. Never enable the local/test
profile on a shared deployment.

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/learning-sessions` | Propose a session; returns 201 and a Location header |
| GET | `/api/learning-sessions` | Paginated sessions involving the current user |
| GET | `/api/learning-sessions/{id}` | Read one of the current user's sessions |
| PATCH | `/api/learning-sessions/{id}` | Change status with `{"status":"accepted"}`, `declined`, or `canceled` |

Example proposal (choose a future date):

```json
{
  "matchRequestId": 1,
  "teacherId": "00000000-0000-0000-0000-000000000001",
  "scheduledStart": "2027-01-15T14:00:00-06:00",
  "scheduledEnd": "2027-01-15T15:00:00-06:00",
  "mode": "online",
  "meetingUrl": "https://meet.example.test/study-room",
  "message": "Let's practice Java."
}
```

For `in-person`, supply a nonblank `location` instead of `meetingUrl`. Online
sessions require an absolute HTTP/HTTPS URL with a host and no embedded login
credentials. Irrelevant mode-specific fields are stored as null. Optional text is
trimmed, blank text becomes null, and each optional text field is limited to
2,000 characters. Dates must include a UTC offset, start in the future, and end
after the start. Values are normalized to UTC with microsecond precision, matching
PostgreSQL `timestamptz`; clients format them in the viewer's time zone.

The list accepts `direction=all` (default), `incoming`, or `outgoing`, an optional
positive `matchRequestId`, `offset` (default 0), and `limit` (default 20, maximum
100). Incoming means proposed by the other participant, regardless of teaching
role. It returns `{sessions,total,offset,limit}`, ordered by scheduled start and
ID. Both the list and total include only the current user's sessions.

Responses contain `id`, `matchRequestId`, `proposedBy`, `teacherId`, `learnerId`,
`scheduledStart`, `scheduledEnd`, `mode`, `location`, `meetingUrl`, `status`,
`createdAt`, and `message`. Session modes and statuses use the lowercase values in
the supplied table. The existing match-request API still uses its capitalized
status labels; its contract is unchanged.

## Permissions and transitions

Either participant may propose. The chosen teacher must teach the matched skill
and the other participant must learn it. The match must be accepted; both users
must remain active, onboarded, in the same configured supported school, and not
block each other. Proposal and acceptance both recheck these conditions.

Only the participant other than `proposedBy` may accept or decline a pending
proposal. Either participant may cancel a pending or accepted future session.
Declined/canceled sessions cannot change, and a session cannot change after its
start time. Decline/cancel remain available if eligibility subsequently changes.
No status can reset to pending. Conditional updates reject stale or competing
transitions; profile rows are locked in the matching feature's stable UUID order.

Nonparticipants and missing IDs return 404; wrong participant actions return 403;
invalid dates/details return 400; changed or ineligible match/session states
return 409; account/school/block/skill incompatibility returns 422. An API without
a configured production issuer denies access. Missing/invalid Bearer tokens are
rejected by the shared security chain before controller execution.

## Database and availability integration

Persistence uses `session_id bigint`, `match_request_id`, `proposed_by`,
`teacher_id`, `learner_id`, `scheduled_start`, `scheduled_end`, `mode`, `location`,
`meeting_url`, `status`, `created_at`, and `message`. Stored status values are
`pending`, `accepted`, `declined`, and `canceled`; modes are `online` and
`in-person`. The local H2 and disposable PostgreSQL fixtures include this table.
Production initialization remains disabled: no live schema or RLS change runs.

Future Smart Scheduling can use #16's private availability API and profile IANA
time zones to convert recurring windows to date-specific instants. This manual
API does not require sessions to fit weekly availability, prevent overlapping
appointments, recommend times, emit notifications, or create meeting links.
Those are separate integrations, as specified by #18. A trusted backend database
identity needs access to this table; direct Supabase access still requires #19's
RLS/grants review and #5's real login integration.

## Verification

`./mvnw test` runs H2 HTTP/business tests, including both teaching/proposing
directions, privacy, filters, transitions, eligibility changes, time zones,
invalid input, and local CSRF. The shared JWT tests cover the new collection and
item paths with verified RSA/EC identities and invalid-token rejection.

The opt-in `PostgresMatchContractTests` use the disposable loopback database
described in [availability/discovery testing](availability-discovery.md#real-postgresql-validation).
They also verify session bigint keys, nullable fields, role/time round-trips,
stored values, parent foreign keys, and competing accept/decline operations on
the real PostgreSQL engine. These tests do not connect to the shared Supabase
database or certify deployed RLS permissions.

Validated for this change: all 143 tests passed on H2 and disposable PostgreSQL
17 with zero failures, errors, or skips. The executable JAR's local HTTP demo
also passed twice, including match reuse, both session modes, cookies/CSRF,
accept/decline/cancel, and private listing. Playwright exercised the actual local
application with two accounts: accepted-match navigation, role selection, online
and in-person proposals, status actions and confirmations, stale-state refresh,
filters, pagination, text-only notes, and responsive layouts down to 320px.
Sign-in/error response branches and the production Bearer hook were also tested
with controlled responses; real JWT verification remains covered by the server
tests. Browser checks had no JavaScript errors or missing static assets.
