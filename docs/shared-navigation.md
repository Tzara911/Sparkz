# Shared navigation and account controls

This UI branch is based on main. It contains no learning-session backend,
database, or scheduling-page changes; those remain in PR #33 / Issue #18.

Home, Browse, Match requests, and the Availability page from #32 load
`sparkz-shell.js` and `sparkz-theme.css`.
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
My profile opens the existing read-only profile overview. Account settings
remains disabled. Manage availability is enabled only when its page is present;
incomplete users also see Onboarding. These destinations are never separate primary tabs.

The `data-login-page` body attribute makes Login an ordinary link to the
independent `/login.html` page from #32. `data-availability-page` enables the
Profile menu's `/availability.html` link and the optional public-day summary.
All four pages in #32 set both flags. The independent UI branch omits them
until those pages are integrated, retaining its provider/unavailable states.
The two branches share the exact same shell script and stylesheet; adding the
page flags does not introduce another header implementation.

The existing #5 integration may provide this adapter:

```js
window.sparkzAuth = {
  async getAccessToken() { /* current Supabase access token */ },
  async signIn() { /* the team's Google sign-in flow */ },
  async signOut() { /* terminate the actual Supabase session */ }
};
```

The fallback Google sign-in dialog and sign-out action require their provider
hooks. When a standalone login page exists, its public link stays usable without
a provider hook. After a hook resolves, the server is checked again. A 401/403 clears account state, a
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

Both application packages build successfully, with 10 focused security/JWT
tests passing on each branch. Browser checks cover the independent UI branch
at 12 widths on three pages (36 cases), and the #32 integration at 13 widths on
four pages (52 cases). They verify identical shared header/footer structure and
geometry, the exact four primary tabs, anonymous Login and actual locally
authenticated Profile states, mobile/keyboard menus, the standalone login route,
and controlled provider lifecycle/error responses. No browser JavaScript errors,
missing assets, or horizontal overflow were found.
