# Issue #10 execution report

Run date: November 1, 2026 (America/Chicago).

Source baseline: #21 `34dfbab2ed2852f193a2cf8fe5d23a949c86436c`, included by
#23 `87b465973b2561263c2735b2085d3a5370388baa`.
PR #24: `4cd645877fc2fb9a6e8238d2d80eb1fb890304f7`.
PR #25: `a68ab2a08c2f3583453bcf1c309289e7964d786f`.

Executed with Java 21 (project release 17), Maven 3.9.16, isolated H2, real Spring
HTTP/security/persistence components, and local test JWT signing/JWKS. No production
Supabase connection or writes. Source hashes and JUnit XML are retained in each
temporary run directory printed by the runner.

## Results

| Scenario | Executed result | Evidence directory in this workspace |
| --- | --- | --- |
| Ordinary #21/#23 regression, including the new JWT journey tests | 63 passed; 8 optional PostgreSQL tests skipped; no failures/errors; Maven exit 0 | `/tmp/sparkz-mvp-regression-yyhp0j2k` |
| Aligned #21/#23 acceptance and local configuration | 5 passed; Maven exit 0 | `/tmp/sparkz-mvp-aligned-b0aac44k` |
| #21/#23 + unchanged #24 | 8 passed; Maven exit 0 | `/tmp/sparkz-mvp-pr24-pcx6lyy6` |
| #21/#23 + unchanged #25, JWT profile | 10 passed; Maven exit 0 | `/tmp/sparkz-mvp-pr25-r1u7xmds` |
| #21/#23/#24/#25 together | Startup failed; 16 test errors; Maven exit 1 | `/tmp/sparkz-mvp-combined-qyievv9h` |
| #21/#23 + #25, local profile | Startup failed; 1 test error; Maven exit 1 | `/tmp/sparkz-mvp-pr25-local-xo3b2at5` |

Rows overlap: the four matching journey cases run in multiple combinations. Do not
sum these counts as distinct coverage. The 16 combined errors are consequences of
one startup failure, not evidence of 16 separate behavioral defects. Neither
`CrossFeatureIT` case reached its API assertions. Temporary paths are execution
evidence, not portable repository artifacts; rerunning produces new paths.

## Blocking: duplicate shared Skill components

Affected files:

- #24 [`SkillController.java:15`](https://github.com/Tzara911/Sparkz/blob/4cd645877fc2fb9a6e8238d2d80eb1fb890304f7/src/main/java/com/sparkz/sparkz/SkillController.java#L15)
- #25 [`skills/SkillController.java:13`](https://github.com/Tzara911/Sparkz/blob/a68ab2a08c2f3583453bcf1c309289e7964d786f/src/main/java/com/sparkz/sparkz/skills/SkillController.java#L13)

Actual combined startup error: `ConflictingBeanDefinitionException` reports that
`skillController` for `com.sparkz.sparkz.skills.SkillController` conflicts with
`com.sparkz.sparkz.SkillController`. Both also map `GET /api/skills`; corresponding
SkillService/SkillRepository names overlap as identified by source review.

Minimum correction: #24 and #25 owners agree on one shared catalog implementation
and wire user-skill operations to it. Renaming only the controller bean does not
resolve duplicate routes or remaining component names. Do not enable bean
overriding to hide the collision. Preserve the agreed catalog ID/description
contract, then rerun `combined` and the per-feature scenarios.

Proposed coordination: emilee-meta (#24) and amfost3-stack (#25). No GitHub
assignment or message has been sent by this test task.

## Blocking: duplicate local login configuration

Affected files:

- #25 [`profiles/LocalLoginConfig.java:26`](https://github.com/Tzara911/Sparkz/blob/a68ab2a08c2f3583453bcf1c309289e7964d786f/src/main/java/com/sparkz/sparkz/profiles/LocalLoginConfig.java#L26)
- #23 [`matching/LocalMatchSecurity.java:16`](https://github.com/Tzara911/Sparkz/blob/87b465973b2561263c2735b2085d3a5370388baa/src/main/java/com/sparkz/sparkz/matching/LocalMatchSecurity.java#L16)

Actual local startup error: `BeanDefinitionOverrideException` because both define
`demoUsers` under the local/test profiles. The JWT-profile pass does not exercise
these local beans. Source inspection also shows overlapping security chains and
different CSRF policies; the startup failure prevents testing their combined behavior.

Minimum correction: consolidate local demo authentication and security-chain
ownership, with an explicit shared CSRF contract and schema/seed initialization
path. Preserve the matching demonstration identities and eligibility data, then
rerun `pr25-local`, `combined-local`, and the ordinary integrated suite. Do not
solve this by permitting arbitrary bean overriding.

Proposed coordination: amfost3-stack (#25) with Tzara911 (#7/#8). Production Google
login remains #5's responsibility.

## Verification still required

- Real Google/Supabase login, refresh, logout and first-login profile creation.
- PostgreSQL execution of #25's repository writes. Source inspection still shows
  H2-style `MERGE ... KEY` in
  [`UserSkillRepository.java:47`](https://github.com/Tzara911/Sparkz/blob/a68ab2a08c2f3583453bcf1c309289e7964d786f/src/main/java/com/sparkz/sparkz/skills/UserSkillRepository.java#L47).
  Its H2 test passes do not establish PostgreSQL compatibility; a PostgreSQL
  implementation and disposable-database run are needed before Supabase acceptance.
- Live role permissions/RLS, final UI/browser integration, onboarding/account-state
  operations, and full graded class-diagram signature mapping.

These are not marked passed or silently replaced by test endpoints. Detailed
steps are in [the MVP checklist](mvp-testing.md#remaining-mvp-checklist).

Verdict: **Needs Verification**. The individual H2/JWT combinations passed; the
full MVP remains blocked by integration startup failures and the pending checks.
