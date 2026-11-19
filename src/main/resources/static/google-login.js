// Google sign-in through Supabase Auth. Independent of login.js (the email/password form),
// but it finishes the same way: GET /api/profiles/me with the access token, then route
// on the profile (same rules as login.js).
(() => {
  // false: stay on this page and show the result (good for first tests).
  // true:  redirect like the email form does ('/browse.html' if onboarded, otherwise '/').
  const REDIRECT_AFTER_LOGIN = true;

  const btn = document.getElementById('google-btn');
  const msg = document.getElementById('login-message');
  let sb; // Supabase client

  function say(text, isError = false) {
    msg.textContent = text;
    msg.dataset.state = isError ? 'error' : 'info';
  }

  async function finishSignIn(session) {
    say('Signing you in…');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch('/api/profiles/me', {
        cache: 'no-store',
        signal: controller.signal,
        headers: {
          Accept: 'application/json',
          'X-Requested-With': 'XMLHttpRequest',
          Authorization: 'Bearer ' + session.access_token,
        },
      });

      if (response.status === 401 || response.status === 403) {
        say('Signed in with Google, but the server did not accept the token (' + response.status + '). See the note about the /api/profiles security chain.', true);
        return;
      }
      if (response.status === 404) {
        say('Signed in, but no profile row exists yet.');
        if (REDIRECT_AFTER_LOGIN) window.location.assign('/');
        return;
      }
      if (!response.ok) {
        say('Your sign-in could not be verified (' + response.status + '). Please try again.', true);
        return;
      }

      const profile = await response.json();
      if (!profile || typeof profile.userId !== 'string') {
        say('Your account could not be loaded. Please try again.', true);
        return;
      }

      say('Signed in as ' + (profile.email || session.user.email) + '.');
      if (REDIRECT_AFTER_LOGIN) {
        window.location.assign(profile.onboardingCompleted ? '/browse.html' : '/');
      }
    } catch (e) {
      say(e?.name === 'AbortError' || e?.name === 'TypeError'
        ? 'Your sign-in could not be verified. Check your connection and try again.'
        : (e?.message || 'We could not sign you in. Please try again.'), true);
    } finally {
      clearTimeout(timeout);
    }
  }

  async function init() {
    // 1. Public Supabase URL + key come from our Spring app (/api/config).
    let cfg;
    try {
      const res = await fetch('/api/config');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      cfg = await res.json();
    } catch (e) {
      btn.disabled = true;
      say('Google sign-in is unavailable: could not load /api/config (' + e.message + ').', true);
      return;
    }
    sb = window.supabase.createClient(cfg.supabaseUrl, cfg.supabaseKey, {
      auth: { flowType: 'pkce' },
    });

    // 2. Did Supabase send us back with an error?
    const params = new URLSearchParams(location.search);
    if (params.get('error_description')) {
      say(params.get('error_description'), true);
      history.replaceState(null, '', location.pathname);
      return;
    }

    // 3. Back from Google? supabase-js exchanges the ?code= for a session here.
    const { data: { session }, error } = await sb.auth.getSession();
    if (error) { say(error.message, true); return; }
    if (!session) return; // normal page load, nothing to do
    history.replaceState(null, '', location.pathname);

    await finishSignIn(session);
  }

  btn.addEventListener('click', async () => {
    if (!sb) { say('Google sign-in is still loading. Try again in a moment.', true); return; }
    const { error } = await sb.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: location.origin + '/login.html' },
    });
    if (error) say(error.message, true);
  });

  init();
})();