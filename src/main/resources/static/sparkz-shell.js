/* Shared brand shell. #5 supplies sparkzAuth.getAccessToken() for production. */
(() => {
  const page = document.body.dataset.page;
  document.querySelector('[data-sparkz-header]').innerHTML = `
    <header class="site-header"><div class="header-inner">
      <a class="brand" href="/browse.html" aria-label="Sparkz home"><img src="/images/sparkz-logo.png" alt="Sparkz" width="216" height="75"></a>
      <nav class="primary-nav" aria-label="Main navigation">
        <a href="/browse.html" ${page === 'browse' ? 'aria-current="page"' : ''}>Discover</a>
        <a href="/matches.html" ${page === 'matches' ? 'aria-current="page"' : ''}>Match requests</a>
        <a href="/sessions.html" ${page === 'sessions' ? 'aria-current="page"' : ''}>Sessions</a>
        <button class="profile-nav" type="button" data-open-profile>My profile</button>
      </nav>
      <button class="user-avatar" type="button" data-open-profile aria-label="Open my profile">?</button>
    </div></header>`;
  document.querySelector('[data-sparkz-footer]').innerHTML = `
    <footer class="site-footer"><div class="footer-inner">
      <img class="footer-logo" src="/images/sparkz-logo.png" alt="" width="146" height="54">
      <p>Good things happen when we share.</p><span class="footer-spark" aria-hidden="true">✦</span>
    </div></footer>`;
  const dialog = document.createElement('dialog');
  dialog.className = 'profile-dialog';
  dialog.setAttribute('aria-labelledby', 'profile-title');
  dialog.innerHTML = `<form method="dialog"><button class="dialog-close" aria-label="Close profile">×</button></form>
    <h2 id="profile-title">My profile</h2><div id="profile-content" role="status" aria-live="polite"></div>`;
  document.body.append(dialog);
  const content = dialog.querySelector('#profile-content');
  let profilePromise;
  async function loadProfile() {
    if (!profilePromise) profilePromise = (async () => {
      const token = await window.sparkzAuth?.getAccessToken();
      const response = await fetch('/api/profiles/me', {headers: {Accept: 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {})}});
      if (!response.ok) throw new Error('Please sign in to view your profile.');
      const profile = await response.json();
      const name = profile.firstName || profile.username || 'Sparkz member';
      document.querySelector('.user-avatar').textContent = Array.from(name.trim())[0]?.toLocaleUpperCase() || '?';
      return profile;
    })().catch(error => { profilePromise = undefined; throw error; });
    return profilePromise;
  }
  for (const button of document.querySelectorAll('[data-open-profile]')) button.addEventListener('click', async () => {
    if (!dialog.open) dialog.showModal();
    content.textContent = 'Loading your profile…';
    try {
      const profile = await loadProfile();
      content.replaceChildren();
      for (const [text, className] of [[profile.username || profile.firstName || 'Sparkz member', 'profile-name'],
        [profile.schoolName || 'No school added yet', ''], [profile.bio || 'No introduction added yet.', '']]) {
        const p = document.createElement('p');
        p.textContent = text;
        p.className = className;
        content.append(p);
      }
    } catch (error) { content.textContent = error.message; }
  });
  loadProfile().catch(() => { /* The feature page shows the sign-in error. */ });
})();
