(() => {
  const form = document.getElementById('login-form');
  const message = document.getElementById('login-message');
  let signingIn = false;
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (signingIn) return;
    signingIn = true;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    message.textContent = 'Checking your sign-in…';
    try {
      if (typeof window.sparkzAuth?.signIn !== 'function') {
        throw new Error('Sign-in is not available yet. Please try again later.');
      }
      const result = await window.sparkzAuth.signIn({ email: document.getElementById('email').value.trim(), password: document.getElementById('password').value });
      if (result?.error) throw result.error;
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10000);
      try {
        const token = await window.sparkzAuth.getAccessToken?.();
        const response = await fetch('/api/profiles/me', {cache: 'no-store', signal: controller.signal,
          headers: {Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest', ...(token ? {Authorization: `Bearer ${token}`} : {})}});
        if (response.status === 401 || response.status === 403) throw new Error('Sign-in has not completed. Complete sign-in and try again.');
        if (response.status === 404) { window.location.assign('/'); return; }
        if (!response.ok) throw new Error('Your sign-in could not be verified. Please try again.');
        const profile = await response.json();
        if (!profile || typeof profile.userId !== 'string') throw new Error('Your account could not be loaded. Please try again.');
        document.getElementById('password').value = '';
        window.location.assign(profile.onboardingCompleted ? '/browse.html' : '/');
      } finally { clearTimeout(timeout); }
    } catch (error) {
      message.textContent = error?.name === 'AbortError' || error?.name === 'TypeError'
        ? 'Your sign-in could not be verified. Check your connection and try again.'
        : error?.message || 'We could not sign you in. Please try again.';
    } finally {
      button.disabled = false;
      signingIn = false;
    }
  });
})();
