/* Shared brand shell. #5 supplies sparkzAuth.getAccessToken() for production. */
(() => {
  const page = document.body.dataset.page;
  document.querySelector('[data-sparkz-header]').innerHTML = `
    <header class="site-header"><div class="header-inner">
      <a class="brand" href="/browse.html" aria-label="Sparkz home"><img src="/images/sparkz-logo.png" alt="Sparkz" width="216" height="75"></a>
      <nav class="primary-nav" aria-label="Main navigation">
        <a href="/browse.html" ${page === 'browse' ? 'aria-current="page"' : ''}>Discover</a>
        <a href="/matches.html" ${page === 'matches' ? 'aria-current="page"' : ''}>Match requests</a>
        <a class="profile-nav" href="/profile.html" ${page === 'profile' ? 'aria-current="page"' : ''}>My profile</a>
      </nav>
      <a class="user-avatar" href="/profile.html" aria-label="Open my profile">?</a>
    </div></header>`;
  document.querySelector('[data-sparkz-footer]').innerHTML = `
    <footer class="site-footer"><div class="footer-inner">
      <img class="footer-logo" src="/images/sparkz-logo.png" alt="" width="146" height="54">
      <p>Good things happen when we share.</p><span class="footer-spark" aria-hidden="true">✦</span>
    </div></footer>`;
  (async () => {
    try {
      const token = await window.sparkzAuth?.getAccessToken();
      const response = await fetch('/api/profiles/me', {headers: {Accept: 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {})}});
      if (!response.ok) return;
      const profile = await response.json();
      const name = profile.firstName || profile.username || 'Sparkz member';
      document.querySelector('.user-avatar').textContent = Array.from(name.trim())[0]?.toLocaleUpperCase() || '?';
    } catch { /* Keep the placeholder "?" if the profile can't be loaded yet. */ }
  })();
})();
