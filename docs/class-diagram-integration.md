# Class diagram and database integration for #7 / #8

The graded SRS diagram remains the domain design. The latest SQL supplied by the
team is the storage contract. Domain class names do not need to be table names;
repositories and API projections make those mappings explicit.

## Matching operations (#8)

`matching.MatchOperations` implements the four diagram responsibilities. The
Spring controller derives the acting UUID from verified authentication, then calls:

| SRS operation | Application operation | Persistence |
| --- | --- | --- |
| sendRequest(user) | sendRequest(actorUUID, recipientUUID, skillID) | Insert a pending match_requests row after eligibility/duplicate checks |
| acceptRequest(user) | acceptRequest(actorUUID, requestID) | Receiver changes pending to accepted; eligibility is checked again |
| declineRequest(user) | declineRequest(actorUUID, requestID) | Receiver changes pending to declined |
| cancelRequest(user) | cancelRequest(actorUUID, requestID) | Sender changes pending to canceled |

Each write runs in a transaction. The explicit request ID distinguishes repeated
requests between the same people. Methods return a `MatchRequest` result for HTTP
responses. These are concrete application signatures; the diagram's User-typed,
void domain signatures still require an adapter when #24 defines the shared User
class. Do not represent this as literal signature equivalence or replace #24's
classes with request/response records.

The API still uses `Pending`, `Accepted`, `Declined`, `Canceled`. Stored values are
`pending`, `accepted`, `declined`, `canceled`. Earlier supplied reports used
`accept`; this code intentionally targets the **latest** supplied SQL. The app
does not rewrite old rows or change live constraints. Confirm the target database
has the latest status constraint before deployment.

## Shared model contract with #24

| Graded model | Storage / application boundary |
| --- | --- |
| User.uid: String | Validate/convert the Auth UUID string to profiles.user_id (UUID); do not generate a separate user ID |
| User.profile: Profile | #24 owns the 1:1 domain composition; the relational data spans profiles, user_skills and availability tables |
| Skill.skillID / skillName / description | skills.skill_id / skill_name / description; SQL bigint is retained as long in these APIs |
| Profile.teach / learn | Join user_skills to skills; use the composite key (user_id, skill_id, skill_type) with teach/learn roles |
| User.sendRequest | Outgoing match_requests; the domain owner must define whether its list includes terminal requests |
| User.match | The other participant of accepted requests, in either direction; deduplicate users when assembling the domain list |
| User.block / unblock | Current blocks come from blocked_users; unblock history comes from user_block_events |
| Profile.available / unavailable | Detailed intervals/recurrence belong to the shared profile/scheduling model; public weekdays are stored in profile_available_days |

One user may both teach and learn the same skill. Removing one user-skill
association must preserve the catalog and other associations. `Skill` IDs in
#7/#8 remain long to preserve database bigint values; if #24 retains the graded
domain int, use checked conversion at that boundary, not a narrowing cast.

`MatchStore.Profile` is a small internal account-eligibility row, not the SRS
Profile class. HTTP DTO fields can differ from domain attributes; changing them
requires coordination with the frontend. #24's User/Profile/Skill classes and
branch are not modified by these changes.

## Other features and current limits

Public weekday hints use ISO numbering (Monday=1, Sunday=7). Weekly and dated
unavailable intervals do not automatically change public weekdays: exact free-time
calculation requires the user's zone and a requested date range, belongs to
scheduling, and must not disclose private intervals in discovery responses.

Notification storage differs between the latest SQL and the earlier catalog
report. #8's ticket leaves notifications to follow-up integration; this change
does not add notification writes or guess how multiple recipients are stored.
LearningSession, Review and UserOperations are outside #7/#8's feature scope.
Their completed implementations and #24's domain adapters are still necessary
before claiming the whole project reproduces every graded UML signature.

Authentication and ownership remain required at API boundaries. Future profile
and catalog routes must join the shared security configuration when implemented;
protecting match/discovery routes does not automatically secure #24's routes.

## Verification

Normal `bash mvnw test` runs the isolated H2 regression suite. The optional
`PostgresMatchContractTests` uses a disposable local PostgreSQL database to check
real UUID/bigint identities, status checks, foreign keys and concurrent writes.
It is enabled only by a loopback URL matching
`jdbc:postgresql://127.0.0.1:<port>/sparkz_test`. It creates test tables, so use an
empty disposable database, never a database containing application data:

```sh
SPARKZ_POSTGRES_TEST_URL=jdbc:postgresql://127.0.0.1:55432/sparkz_test \
SPARKZ_POSTGRES_TEST_USERNAME=postgres \
SPARKZ_POSTGRES_TEST_PASSWORD=local-test-only \
bash mvnw -Dtest=PostgresMatchContractTests test
```

The schema fixture is hand-written executable test DDL for the tables these
features use. The user's Supabase context export is not executed. These tests do
not establish live Supabase connectivity, actual backend grants or RLS behavior.
