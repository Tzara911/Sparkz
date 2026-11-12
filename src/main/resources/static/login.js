(() => {
  const form = document.getElementById('login-form');
  const message = document.getElementById('login-message');
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    message.textContent = 'Checking your sign-in…';
    try {
      if (typeof window.sparkzAuth?.signIn !== 'function') {
        throw new Error('The Sparkz sign-in service is not connected yet. Authentication is being handled in the shared login integration (#5).');
      }
      await window.sparkzAuth.signIn({ email: document.getElementById('email').value.trim(), password: document.getElementById('password').value });
      window.location.assign('/browse.html');
    } catch (error) {
      message.textContent = error?.message || 'We could not sign you in. Please try again.';
    } finally {
      button.disabled = false;
    }
  });
})();
