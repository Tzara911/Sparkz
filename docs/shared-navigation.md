# Shared navigation and account controls

This UI branch is based on main. It contains no learning-session backend,
database, or scheduling-page changes; those remain in PR #33 / Issue #18.

Home, Browse, and Match requests load `sparkz-shell.js` and `sparkz-theme.css`.
Each page mounts the same header and footer, with only the active tab changing.
The logo and Home return to `/`, and the old Discover label is now Browse.
The approved cream, orange, and brown theme, home hero/cards, and feature-page
content are preserved. `sparkz-layout.css` styles only the home content.

Sessions retains its place in the navigation as a disabled Coming soon entry.
It must become a link to `/sessions.html` when the scheduling page from #33 is
integrated. This branch does not link to a page that main does not yet contain.

## Account controls

Login sits at the right of every header. The shell verifies identity using
`GET /api/profiles/me` before displaying the Profile dropdown and avatar.
My profile opens the existing read-only profile overview. Account settings and
Manage availability remain disabled; incomplete users also see Onboarding.

The existing #5 integration may provide this adapter:

```js
window.sparkzAuth = {
  async getAccessToken() { /* current Supabase access token */ },
  async signIn() { /* the team's Google sign-in flow */ },
  async signOut() { /* terminate the actual Supabase session */ }
};
```

Sign-in and sign-out are enabled only when their provider hooks exist. After a
hook resolves, the server is checked again. A 401/403 clears account state, a
404 keeps an authenticated account awaiting profile setup, and network/server
failures retain the last verified state with an error message. Tokens are not
stored by the shell, and logout is never simulated. Google OAuth remains #5.

The shell rechecks authentication on `sparkz:auth-changed` events and when the
tab becomes visible. Discovery and match requests announce successful local
authentication. Background checks use `X-Requested-With: XMLHttpRequest` to
avoid an unsolicited Basic-auth prompt on anonymous Home visits.

## Mobile and keyboard behavior

The mobile menu expands the same navigation and leaves the account control at
the top right. Opening the account menu closes mobile navigation. Arrow keys,
Home/End, Escape, outside clicks, and focus return are supported. Unimplemented
destinations are disabled rather than linked to missing pages.

## Verification

The packaged application builds successfully and all 10 focused security/JWT
tests pass. Browser checks cover the three existing pages at 12 viewport widths
(36 page/width combinations), matching header/footer structure and geometry,
anonymous and actual local Basic-authenticated account states, mobile and
keyboard menus, and controlled provider lifecycle/error responses. No browser
JavaScript errors, missing assets, or horizontal overflow were found.
