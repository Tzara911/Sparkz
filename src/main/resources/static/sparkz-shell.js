/* One shell for every page. #5 supplies the optional sparkzAuth provider adapter. */
(() => {
  const header = document.querySelector('[data-sparkz-header]');
  const footer = document.querySelector('[data-sparkz-footer]');
  if (!header || !footer) return;
  const page = document.body.dataset.page;
  const links = [['home', 'Home', '/'], ['browse', 'Browse', '/browse.html'],
    ['matches', 'Match requests', '/matches.html'], ['sessions', 'Sessions', '/sessions.html']];
  const chevron = '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m4 6 4 4 4-4"/></svg>';
  header.innerHTML = `<header class="site-header"><div class="header-inner">
    <a class="brand" href="/" aria-label="Sparkz home"><img src="/images/sparkz-logo.png" alt="Sparkz" width="216" height="75"></a>
    <button class="nav-toggle" type="button" aria-label="Toggle navigation" aria-expanded="false" aria-controls="sparkz-navigation"><span aria-hidden="true">☰</span></button>
    <nav class="primary-nav" id="sparkz-navigation" aria-label="Main navigation">${links.map(([key, label, href]) => key === 'sessions'
      ? `<span class="nav-unavailable" aria-disabled="true" title="Coming soon">${label}</span>`
      : `<a href="${href}"${key === page ? ' aria-current="page"' : ''}>${label}</a>`).join('')}</nav>
    <div class="account-wrap">
      <button class="login-button" type="button" disabled aria-busy="true">Login</button>
      <button class="account-toggle" type="button" hidden aria-label="Profile menu" aria-haspopup="menu" aria-expanded="false" aria-controls="sparkz-account-menu"><span class="account-avatar" aria-hidden="true"></span><span class="account-label">Profile</span>${chevron}</button>
      <div class="account-menu" id="sparkz-account-menu" role="menu" aria-label="Account" hidden>
        <p class="account-name" role="presentation"></p>
        <button type="button" role="menuitem" data-shell-profile>My profile</button>
        <button type="button" role="menuitem" disabled aria-disabled="true">Account settings <span class="menu-note">Coming soon</span></button>
        <button type="button" role="menuitem" disabled aria-disabled="true">Manage availability <span class="menu-note">Coming soon</span></button>
        <button type="button" role="menuitem" data-shell-onboarding disabled aria-disabled="true">Onboarding <span class="menu-note">Coming soon</span></button>
        <div class="account-separator" role="separator"></div>
        <button type="button" role="menuitem" data-shell-sign-out disabled aria-disabled="true">Sign out</button>
      </div>
    </div>
  </div><p class="shell-status" role="status" aria-live="polite" hidden></p></header>`;
  footer.innerHTML = `<footer class="site-footer"><div class="footer-inner">
    <a href="/" aria-label="Sparkz home"><img class="footer-logo" src="/images/sparkz-logo.png" alt="Sparkz" width="146" height="54"></a>
    <p>Good things happen when we share. <span class="footer-spark" aria-hidden="true">✦</span></p>
    <span class="footer-copyright">© ${new Date().getFullYear()} Sparkz</span>
  </div></footer>`;
  const query = selector => header.querySelector(selector);
  const toggle = query('.nav-toggle'), navigation = query('.primary-nav');
  const login = query('.login-button'), account = query('.account-toggle'), menu = query('.account-menu');
  const signOut = query('[data-shell-sign-out]');
  let profile = null, authenticated = false, authRevision = 0;
  const status = text => { query('.shell-status').textContent = text; query('.shell-status').hidden = !text; };
  const menuItems = () => [...menu.querySelectorAll('[role="menuitem"]:not(:disabled)')];
  function closeAccount(focus = false) {
    menu.hidden = true;
    account.setAttribute('aria-expanded', 'false');
    if (focus) account.focus();
  }
  function closeNavigation(focus = false) {
    navigation.classList.remove('is-open');
    toggle.setAttribute('aria-expanded', 'false');
    if (focus) toggle.focus();
  }
  function openAccount() {
    closeNavigation();
    menu.hidden = false;
    account.setAttribute('aria-expanded', 'true');
    menuItems()[0]?.focus();
  }
  toggle.addEventListener('click', () => {
    const open = toggle.getAttribute('aria-expanded') !== 'true';
    closeAccount();
    toggle.setAttribute('aria-expanded', String(open));
    navigation.classList.toggle('is-open', open);
  });
  account.addEventListener('click', () => menu.hidden ? openAccount() : closeAccount());
  account.addEventListener('keydown', event => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      openAccount();
      if (event.key === 'ArrowUp') menuItems().at(-1)?.focus();
    }
  });
  menu.addEventListener('keydown', event => {
    const items = menuItems(), current = items.indexOf(document.activeElement);
    if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length-1
        : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
      items[next]?.focus();
    }
  });
  header.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    if (!menu.hidden) { event.preventDefault(); closeAccount(true); }
    else if (navigation.classList.contains('is-open')) { event.preventDefault(); closeNavigation(true); }
  });
  document.addEventListener('click', event => {
    if (!query('.account-wrap').contains(event.target)) closeAccount();
    if (!header.contains(event.target)) closeNavigation();
  });
  document.addEventListener('focusin', event => {
    if (!query('.account-wrap').contains(event.target)) closeAccount();
  });
  window.matchMedia('(max-width: 1000px)').addEventListener('change', () => { closeNavigation(); closeAccount(); });

  const profileDialog = document.createElement('dialog');
  profileDialog.className = 'profile-dialog';
  profileDialog.setAttribute('aria-labelledby', 'shell-profile-title');
  profileDialog.innerHTML = `<form method="dialog"><button class="dialog-close" aria-label="Close profile">×</button></form><h2 id="shell-profile-title">My profile</h2><div class="profile-content" role="status" aria-live="polite"></div>`;
  document.body.append(profileDialog);
  profileDialog.addEventListener('close', () => (account.hidden ? login : account).focus());
  const loginDialog = document.createElement('dialog');
  loginDialog.className = 'profile-dialog auth-dialog';
  loginDialog.setAttribute('aria-labelledby', 'shell-login-title');
  loginDialog.innerHTML = `<form method="dialog"><button class="dialog-close" aria-label="Close sign in">×</button></form><h2 id="shell-login-title">Sign in to Sparkz</h2><p>Connect with other students and plan your next skill exchange.</p><button class="provider-sign-in" type="button">Continue with Google</button><p class="auth-message" role="status" aria-live="polite"></p><button class="auth-retry" type="button">Check sign-in again</button>`;
  document.body.append(loginDialog);
  loginDialog.addEventListener('close', () => (account.hidden ? login : account).focus());
  const providerButton = loginDialog.querySelector('.provider-sign-in');
  const authMessage = loginDialog.querySelector('.auth-message');
  function updateProvider() {
    const available = typeof window.sparkzAuth?.signIn === 'function';
    providerButton.disabled = !available;
    authMessage.textContent = available ? '' : 'Sign-in is not available yet. Please try again later.';
  }
  function renderAccount() {
    login.hidden = authenticated;
    account.hidden = !authenticated;
    query('.account-name').textContent = profile?.username || profile?.firstName || 'Your account';
    query('.account-avatar').textContent = Array.from((profile?.firstName || profile?.username || '?').trim())[0]?.toLocaleUpperCase() || '?';
    query('[data-shell-onboarding]').hidden = Boolean(profile?.onboardingCompleted);
    signOut.disabled = typeof window.sparkzAuth?.signOut !== 'function';
    signOut.setAttribute('aria-disabled', String(signOut.disabled));
    signOut.title = signOut.disabled ? 'Sign-out is not available for this session yet.' : '';
    if (!authenticated) {
      closeAccount();
      profileDialog.querySelector('.profile-content').replaceChildren();
      if (profileDialog.open) profileDialog.close();
    } else if (loginDialog.open) loginDialog.close();
  }
  async function refreshAuth() {
    const current = ++authRevision;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const token = await window.sparkzAuth?.getAccessToken();
      const response = await fetch('/api/profiles/me', {cache: 'no-store', signal: controller.signal,
        headers: {Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest', ...(token ? {Authorization: `Bearer ${token}`} : {})}});
      if (current !== authRevision) return authenticated;
      if (response.ok) {
        const data = await response.json();
        if (current !== authRevision) return authenticated;
        if (!data || typeof data !== 'object') throw new Error('Your account could not be loaded.');
        profile = data;
        authenticated = true;
      } else if (response.status === 404) {
        // Authentication passed, but #5 has not created the user's profile yet.
        profile = null;
        authenticated = true;
      } else if (response.status === 401 || response.status === 403) {
        profile = null;
        authenticated = false;
      } else throw new Error('Could not check sign-in status. Please try again.');
      status('');
      renderAccount();
      return authenticated;
    } catch (error) {
      if (current === authRevision) status(error?.name === 'AbortError' ? 'Sign-in status could not be checked. Please try again.' : error?.message || 'Could not check sign-in status. Please try again.');
      return authenticated;
    } finally {
      clearTimeout(timeout);
      if (current === authRevision) { login.disabled = false; login.setAttribute('aria-busy', 'false'); }
    }
  }
  login.addEventListener('click', () => {
    closeNavigation();
    updateProvider();
    if (!loginDialog.open) loginDialog.showModal();
  });
  providerButton.addEventListener('click', async () => {
    if (typeof window.sparkzAuth?.signIn !== 'function') return;
    providerButton.disabled = true;
    authMessage.textContent = 'Signing in…';
    try {
      await window.sparkzAuth.signIn();
      if (!(await refreshAuth())) authMessage.textContent = 'Complete sign-in, then return here and check your sign-in status.';
    } catch (error) { authMessage.textContent = error?.message || 'Sign-in could not be completed. Please try again.'; }
    finally { providerButton.disabled = typeof window.sparkzAuth?.signIn !== 'function'; }
  });
  loginDialog.querySelector('.auth-retry').addEventListener('click', async event => {
    event.target.disabled = true;
    try { if (!(await refreshAuth())) updateProvider(); }
    finally { event.target.disabled = false; }
  });
  query('[data-shell-profile]').addEventListener('click', async () => {
    closeAccount();
    const content = profileDialog.querySelector('.profile-content');
    content.textContent = 'Loading your profile…';
    if (!profileDialog.open) profileDialog.showModal();
    if (!(await refreshAuth())) return;
    content.replaceChildren();
    for (const [text, className] of profile ? [[profile.username || profile.firstName || 'Sparkz member', 'profile-name'],
      [profile.schoolName || 'No school added yet', ''], [profile.bio || 'No introduction added yet.', '']]
      : [['Complete your profile to get started.', '']]) {
      const p = document.createElement('p');
      p.textContent = text;
      p.className = className;
      content.append(p);
    }
  });
  signOut.addEventListener('click', async () => {
    if (typeof window.sparkzAuth?.signOut !== 'function') return;
    signOut.disabled = true;
    closeAccount();
    try {
      await window.sparkzAuth.signOut();
      if (await refreshAuth()) status('You are still signed in. Please try signing out again.');
      else window.location.assign('/');
    } catch (error) { status(error?.message || 'Sign-out could not be completed. Please try again.'); }
    finally { signOut.disabled = typeof window.sparkzAuth?.signOut !== 'function'; }
  });
  window.sparkzShell = {refreshAuth};
  window.addEventListener('sparkz:auth-changed', refreshAuth);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshAuth(); });
  refreshAuth();
})();
