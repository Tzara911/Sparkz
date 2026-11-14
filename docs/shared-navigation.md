Shared navigation and account controls

Home, Browse, Match requests, Sessions, and Availability load `sparkz-shell.js`
and `sparkz-theme.css`. Each page has one `data-sparkz-header` and
`data-sparkz-footer` mount and sets `body[data-page]` to `home`, `browse`,
`matches`, `sessions`, or `availability`. The four primary tabs are Home,
Browse, Match requests, and Sessions. Sessions links to `/sessions.html`.
Availability belongs to the Profile dropdown, rather than the primary tabs.
`aria-current="page"` highlights the active destination. The logo and Home
return to `/`; the previous Discover label is Browse.

`sparkz-layout.css` styles only the home hero and three introduction cards.
The home Bootstrap stylesheet is separate from the shared navigation styles.
Feature content, filtering, matching, availability, and session forms retain
their existing layout and API contracts.

## Account controls

Login sits at the right of every header. The shell verifies identity using
`GET /api/profiles/me` before displaying the Profile dropdown and avatar.
My profile opens the existing read-only profile overview. Account settings
remains disabled. Incomplete users also see the disabled Onboarding entry;
completed users do not. Unimplemented destinations are not linked to missing pages.

The `data-login-page` body attribute makes Login an ordinary link to the
independent `/login.html` page from #32. `data-availability-page` enables the
Profile menu's `/availability.html` link and the optional public-day summary.
Home, Browse, Match requests, Sessions, and Availability all set both flags.
Without them, the same shell retains its provider/unavailable states.

## Authentication integration for #5

The existing #5 integration may provide this adapter before the shell runs:

```js
window.sparkzAuth = {
  async getAccessToken() { /* current Supabase access token */ },
  async signIn() { /* the team's Google sign-in flow */ },
  async signOut() { /* terminate the actual Supabase session */ }
};
```

`getAccessToken()` is also the existing feature-page contract. The standalone
Login page remains accessible without a provider hook. The fallback Google
sign-in dialog and sign-out action require their provider hooks, including in
the local Basic demo. The shell never stores tokens, clears unrelated browser
storage, or hides the account to simulate logout. Google OAuth remains #5.

After a provider hook resolves, the shell checks the server again. A successful
profile response shows Profile, the avatar initial, and the server-returned name.
A 401/403 clears account state and the profile overview. A 404 keeps an
authenticated account awaiting profile setup. Network/server errors retain the
last verified state and display an error. Confirmed sign-out returns to Home.
Newer responses take precedence over older requests.

Authentication changes elsewhere can call `window.sparkzShell.refreshAuth()` or
`window.dispatchEvent(new Event('sparkz:auth-changed'))`. The shell also rechecks
when the tab becomes visible. Discovery, match requests, and sessions announce
successful local authentication. Background checks send
`X-Requested-With: XMLHttpRequest` to avoid an unsolicited Basic-auth prompt on
anonymous Home visits.

## Mobile and keyboard behavior

The mobile menu expands the same navigation and leaves the account control at
the top right. Opening the account menu closes mobile navigation. Arrow keys,
Home/End, Escape, outside clicks, and focus return are supported.

## Verification

Run the focused security/session/JWT tests with:

```sh
./mvnw -Dtest=MatchSecurityTests,MatchSecurityStartupTests,MatchJwtTests,LearningSessionTests test
```

The security regression tests cover public pages and shared assets, private
session API protection, and background profile checks without a Basic-auth
challenge. Existing token tests validate the production JWT routes.

See [the frontend guide](pr32-frontend.md) for the isolated local-profile
availability and Login browser runner, and [the session guide](learning-sessions.md)
for the two-account scheduling demo. Check all five shared-shell pages for active
navigation, Login/Profile state, mobile and keyboard menus, and the standalone
Login and availability links, including when entering from Sessions.
Controlled provider responses do not certify live Google OAuth or Supabase RLS.
