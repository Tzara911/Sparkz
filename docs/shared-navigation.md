# Shared navigation and account controls

Home, Browse, Match requests, and Sessions all load `sparkz-shell.js` and
`sparkz-theme.css`. Each page has one `data-sparkz-header` and
`data-sparkz-footer` mount and sets `body[data-page]` to `home`, `browse`,
`matches`, or `sessions`. The navigation structure, logo, account control, and
footer are identical; `aria-current="page"` highlights the active destination.
The logo and Home always return to `/`. The previous Discover label is Browse.

`sparkz-layout.css` now styles only the home hero/cards. The separate home
navigation script was removed. Feature content, filtering, matching, and session
forms keep their existing layout and API contract.

## Behavior

On a narrow screen, the hamburger button expands the same navigation. It can be
closed with Escape or an outside click. The account control stays at the top
right. Profile opens a separate account menu; opening it closes the mobile
navigation. Keyboard users can move through enabled items with arrow keys,
Home/End, Tab, and Escape. Escape returns focus to the triggering button.

My profile opens the existing read-only profile overview. Account settings and
Manage availability are disabled with a Coming soon label because their pages do
not yet exist. Incomplete users also see Onboarding; completed users do not.
No unavailable destination is linked to a missing HTML file.

## Authentication integration for #5

The shell verifies the current identity with `GET /api/profiles/me`. A successful
response shows Profile, avatar initial, and the server-returned name. A 401/403
shows Login and clears the profile overview. A 404 means the authenticated user
still needs a profile, and keeps the account control available. A network/server
error retains the last verified account state and displays an error; it does not
pretend that sign-in or sign-out succeeded.

Background profile checks send `X-Requested-With: XMLHttpRequest`; this avoids a
native Basic-auth challenge on the anonymous local home page. Matching, discovery,
and session pages notify the shell after their authenticated local/demo requests
succeed, so the header updates after the Basic login flow as well.

#5 may provide this adapter before the shell runs:

```js
window.sparkzAuth = {
  async getAccessToken() { /* return the current Supabase access token */ },
  async signIn() { /* start/complete the team's Google sign-in flow */ },
  async signOut() { /* terminate the actual Supabase session */ }
};
```

`getAccessToken()` is the existing feature-page contract. Login's Google button
is enabled only when `signIn()` exists. Without it, the dialog clearly explains
that sign-in is not available yet. Sign out is similarly disabled without its
provider hook, including in the local Basic demo. The shell never stores tokens,
clears unrelated browser storage, or hides the account to fake logout.

After a provider hook resolves, the shell rechecks the server. Confirmed sign-out
returns to the public home page. Authentication changes elsewhere can call
`window.sparkzShell.refreshAuth()` or dispatch
`window.dispatchEvent(new Event('sparkz:auth-changed'))`. The shell also rechecks
when returning to a visible browser tab. Newer responses take precedence over
older requests so a delayed response cannot restore a previous identity.

These hooks do not implement Google OAuth, account settings, onboarding, or
availability editing. Those features remain their existing tickets; the shared
shell supplies consistent, honest entry states for their integration.

## Verification

The Spring security regression test verifies public access to all four pages and
shared assets, private session API protection, and background profile checks
without a Basic-auth challenge. Existing token tests continue to validate the
production JWT routes.

Browser checks cover consistent navigation/footer markup and geometry on all four
pages, the home and Browse body structures, active tabs, navigation between pages,
Login/Profile states, keyboard and outside-click dismissal, mobile widths,
read-only profile data, and unavailable actions. Provider lifecycle/error cases
use controlled auth-adapter/API responses; the local authenticated state is also
checked against the actual application with Basic credentials.

Verified for this update: all 54 focused security/session/JWT tests pass. Browser
checks pass for 48 page/viewport combinations (four pages, 320–1440px), identical
logged-out/logged-in shell markup and geometry, provider lifecycle/error states,
and the complete two-account session-page regression. No JavaScript errors or
missing static assets were found. The provider lifecycle checks use controlled
responses; real Google OAuth still belongs to #5.
