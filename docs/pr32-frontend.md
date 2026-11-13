# Login and availability frontend (#32)

PR #32 supplies the independent Login page and availability editor. Home stays
at `/`; its Login link opens `/login.html`, and the login-page brand returns
Home. Home, Browse, Match requests, and Availability share the approved header
and footer. Primary tabs are Home, Browse, Match requests, and Sessions, with
the account control on the right. Sessions remains disabled until #33 is
integrated. Availability is accessed from the Profile menu.

## Availability behavior

The editor's controls start disabled in HTML and stay disabled until a valid
availability snapshot is loaded. Failed reads expose Retry loading and cannot
submit an empty replacement. One in-flight operation locks all edit/save/remove
controls, including submissions made with Enter, so separate forms cannot race.

Removing a previously saved day asks for confirmation because the API deletes
that day's detailed slots. Retained days keep their slots. Saving days preserves
a valid active edit and its draft times; deleting its slot resets the editor to
Add time slot. A slot removed by another window is reconciled after a 404/409,
allowing a fresh add rather than repeatedly submitting the deleted ID.

The editor distinguishes a rejected write from a confirmed write whose follow-up
read failed. Unconfirmed network outcomes and failed refreshes lock editing until
a successful read, avoiding blind retries that could duplicate an already saved
slot. Validation errors retain the user's draft. Recurring times are displayed
with the profile time zone; they are not converted to the device's zone.

`/availability.html?demo=1` uses fictional browser-local data and never writes to
the availability API. Changes are labeled as demo-only and survive a reload.

## Authentication contract and dependency

Google OAuth configuration, account creation, onboarding, and the real Supabase
session belong to #5 and the profile integration. This frontend does not provide
or pretend to provide those services. The existing adapter contract is:

```js
window.sparkzAuth = {
  async signIn(credentials) { /* shared provider flow; throw or return {error} on failure */ },
  async getAccessToken() { /* current verified Supabase access token, if available */ },
  async signOut() { /* actual provider sign-out */ }
};
```

After `signIn` resolves, Login checks `GET /api/profiles/me`, forwarding a Bearer
token when available. Only a server-confirmed identity can leave the login page.
A completed profile proceeds to Browse; a missing or incomplete profile goes to
Home, where the shared account control reflects its setup state. A 401/403,
network/server failure, or provider rejection stays on Login with a retryable
message. Concurrent submits cannot start duplicate provider calls. No tokens or
passwords are stored by these page scripts.

Real Google sign-in cannot be certified by browser mocks. The regression runner
separately checks the real local Basic-authenticated server, controlled adapter
responses, and the unavailable-provider state. Backend JWT claims are covered by
the existing Maven tests. Live Google OAuth and Supabase RLS are separate checks.

## Run the frontend regression checks

Use an isolated local-profile app with disposable H2 data. The runner resets
availability for the fictional UUID ending in `0001`; never point it at a
production database. It rejects non-loopback URLs and requires the local-only
demo authentication helper.

```sh
./mvnw package
java -jar target/sparkz-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

In another terminal, install the browser-test dependency outside the repository:

```sh
npm install --prefix /tmp/sparkz-ui-checks playwright@1.62.1
/tmp/sparkz-ui-checks/node_modules/.bin/playwright install chromium
NODE_PATH=/tmp/sparkz-ui-checks/node_modules \
  SPARKZ_BASE_URL=http://127.0.0.1:8080 \
  node scripts/test_pr32_frontend.cjs
```

If Chromium is already installed, `SPARKZ_CHROMIUM=/usr/bin/chromium` selects it.
`SPARKZ_LOCAL_PASSWORD` overrides the documented local demo password, and
`SPARKZ_BROWSER_ARTIFACTS` selects an output directory. The default output is
`/tmp/sparkz-pr32-browser`, containing `results.json` and desktop/mobile captures.

The runner checks both original data-loss/edit-state regressions, failed and
malformed reads, retries, destructive confirmations, retained drafts, remote
deletions, duplicate submits, successful writes followed by failed refreshes,
client/server validation, anonymous access, demo isolation, login verification,
provider failures, account-setup routing, and the approved responsive navigation.

For manual Basic-authenticated local testing, first visit `/api/match-session`
and enter the demo credentials from the availability API guide. The independent
Login page uses the shared #5 provider adapter rather than creating a second
authentication implementation.

## Validation recorded for this update

All 96 Maven tests passed with zero failures, errors, or skips, including seven
contract tests on a disposable PostgreSQL 17 database. The frontend runner passed
13 scenario groups, and the shared-header integration passed 52 page/viewport
combinations across the four existing pages. Browser checks found no JavaScript
exceptions, missing assets, or horizontal overflow. Tests used isolated local H2,
disposable PostgreSQL, and clearly controlled provider responses; they did not
connect to the live Supabase database or certify live Google OAuth.
