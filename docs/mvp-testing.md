# Issue #10: MVP acceptance testing

This test task covers the aligned matching/discovery work (#21/#23), shared classes
(#24), and profile/skill APIs (#25). The source changes in this branch are tests,
fixtures, a test runner and documentation. Teammates' implementations are not copied
into the branch or repaired by the tests.

## What runs

| Suite | Actual implementation required | Acceptance coverage |
| --- | --- | --- |
| `MatchJourneyTests` | #21 + #23 | Signed JWT → discovery/filter → send → cancel → resend → decline → resend → accept; both inboxes; duplicate/terminal rejection; ownership; private data; bigint skill IDs |
| `SharedClassesIT` | #21 + #23 + #24 | Actual JPA profile/catalog mapping, ownership despite a body-supplied ID, profile edits preserving matching fields, profile deletion/cascades, missing rows and authentication |
| `ProfileSkillsIT` | #21 + #23 + #25 | Real profile PUT validation, protected fields, separate teach/learn roles, idempotent associations, own-user isolation, catalog preservation, changing skills/school affecting discovery and request acceptance |
| `CrossFeatureIT` | All four PRs | Both profile APIs share a row; shared catalog → user skills → discovery → accepted match uses the same IDs |
| `LocalConfigurationIT` | Current local demo, optionally with #24/#25 | Full startup with overriding disabled; actual Basic credentials, wrong credentials, and CSRF-protected matching writes |

These are Spring Boot tests with full component scanning, real security filters,
controllers, services and repositories. MockMvc exercises the HTTP dispatch layer
in process. No service mocks, fake profile endpoints, bean-override switches or
component-scan exclusions make integration failures disappear.

`AcceptanceSupport` starts a loopback JWKS server and signs short-lived test JWTs.
This checks backend token verification, not Google sign-in or Supabase Auth itself.
The `mvp-acceptance` profile intentionally exercises the production JWT chain;
`LocalConfigurationIT` separately exercises `local` security. H2 is isolated from
Supabase, uses the existing `db/local-schema.sql` with Hibernate DDL disabled, and
is reset before each acceptance case. Test skill ID `4000000001` exceeds Java int
range. The fixture is not a migration or an exact replica of every Supabase table.

## Run without changing another developer's branch

Requirements: Python 3, Git, JDK 17+, Maven or the existing wrapper. Run from this
test branch. Fetch the PR source objects once if they are not already local:

```sh
git fetch origin refs/pull/24/head refs/pull/25/head
python3 scripts/test-mvp.py regression
python3 scripts/test-mvp.py aligned
python3 scripts/test-mvp.py pr24
python3 scripts/test-mvp.py pr25
python3 scripts/test-mvp.py combined
python3 scripts/test-mvp.py pr25-local
```

Each command creates a new temporary source tree. The runner copies the current
tracked working-tree files and new acceptance tests; it does not copy `.env`,
untracked review probes or build outputs. For PR combinations it overlays the
**unmodified production Java sources** of the pinned PR commits. Different files
at the same path stop preparation rather than selecting a developer's version.
Application resources come from the aligned #23 tree, and the acceptance tests
explicitly select the shared schema and fixture. This checks a deliberate shared
configuration, not either teammate's original standalone seed data or all their
original tests. The local-security classes are included and scanned normally.

The runner strips inherited Supabase/Spring/database-test environment overrides,
does not perform GitHub mutations, and preserves Maven's nonzero exit code on a
failure. Its output directory contains `manifest.json` with refs and source hashes,
`maven.log`, `exit-code.txt`, and `source/target/surefire-reports/`.

The default refs are #24 `4cd6458` and #25 `a68ab2a`. After owners push fixes, fetch
again and choose their exact new commits explicitly:

```sh
python3 scripts/test-mvp.py combined --pr24-ref NEW_PR24_SHA --pr25-ref NEW_PR25_SHA
python3 scripts/test-mvp.py pr25-local --pr25-ref NEW_PR25_SHA
```

Maven options can be passed as `--maven-arg=-o` or `--maven /path/to/mvn`.
The runner never enables optional PostgreSQL tests. Existing PostgreSQL contract
tests remain separately runnable against an empty disposable loopback database
as described in [the integration contract](class-diagram-integration.md).

Once the features are integrated into a normal source checkout, the same tests can
run directly with Maven:

```sh
bash mvnw test
bash mvnw '-Dtest=MatchJourneyTests,SharedClassesIT,ProfileSkillsIT,CrossFeatureIT,LocalConfigurationIT' test
```

`*IT` names are intentionally explicit integration checks; ordinary `mvn test`
does not run them. Selecting an IT without its real feature code fails at the
missing endpoint or during startup. No missing feature is converted into a passing
test or silently skipped. `regression` runs the ordinary suite; the other scenarios
select the acceptance suites in the table above.

## Remaining MVP checklist

| ID | Steps and expected behavior | Dependency / current status |
| --- | --- | --- |
| LOGIN-01 | Complete Google sign-in; backend receives the Auth UUID; first/repeat sign-in creates or reuses exactly one profile | #5; requires actual login implementation and test environment |
| LOGIN-02 | Refresh the token/session, sign out, then retry protected actions; frontend clears its session and unauthenticated calls fail | #5 + production frontend; not established by local JWT tests; logout is not a claim of instant access-token revocation |
| PROFILE-01 | Edit own profile, reload it, attempt to supply another user's ID; only the authenticated user's editable fields change | Automated separately for #24 and #25 |
| PROFILE-02 | Complete onboarding, deactivate/reactivate; discovery eligibility changes accordingly | Remaining #6 behavior; no agreed endpoint in these PRs, so no invented controller or passing stub |
| SKILL-01 | Add teach and learn for the same skill, repeat PUT, remove one role; preserve the other role, another user's association and catalog row | Automated with #25 |
| MATCH-01 | Change skills/school, refresh discovery, submit a stale request or accept after skills change | Automated with #25 + #7/#8 |
| MATCH-02 | Two real users complete cancel, decline and accept flows; reloading both accounts reflects persisted status | Backend automated; production-browser walkthrough still required |
| INTEGRATION-01 | Start all features together, execute both profile APIs and catalog-to-match flow | Blocked by reported startup conflicts |
| DATABASE-01 | Run the profile/skill write flow on disposable PostgreSQL with the supplied schema; check IDs, timezone handling, constraints and SQL dialect | Pending; H2 results do not verify PostgreSQL or Supabase RLS/grants |
| UI-01 | Repeat login/profile/skills/matching flow on the team's final frontend; capture browser errors and screenshots | Final frontend + integrated backend required |

Public weekdays remain 1–7. Detailed weekly/dated unavailable intervals stay private
and belong to the profile/scheduling integration; these tests do not invent that
API. Full SRS domain signature conformance remains a separate code/design review.

Record a failure with the exact source refs, steps, expected/actual result, evidence,
severity and feature owner. After the owner fixes it, record the new refs and rerun
the failing scenario plus affected workflows. Only close #10 after the integrated
flows and remaining applicable checklist items pass before the demonstration.

See [executed results and blockers](mvp-test-report.md).
