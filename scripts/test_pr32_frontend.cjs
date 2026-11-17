/* Run only against the disposable local-profile app; see docs/pr32-frontend.md. */
const {chromium, request} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const target = new URL(process.env.SPARKZ_BASE_URL || 'http://127.0.0.1:8080');
assert.ok(target.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname), 'Use an isolated loopback local-profile app.');
const root = target.origin, user = '00000000-0000-0000-0000-000000000001';
const credentials = {username: user, password: process.env.SPARKZ_LOCAL_PASSWORD || 'local-demo'};
const output = process.env.SPARKZ_BROWSER_ARTIFACTS || '/tmp/sparkz-pr32-browser';
fs.mkdirSync(output, {recursive: true});
const result = {checks: [], errors: [], viewports: []};
const avail = '/api/profiles/me/availability';
const delay = () => new Promise(resolve => setTimeout(resolve, 100));
let browser, api, csrf;
async function state() { const r = await api.get(avail); assert.equal(r.status(), 200); return r.json(); }
async function change(method, endpoint, data) {
  const r = await api.fetch(endpoint, {method, data, headers: {[csrf.csrfHeader]: csrf.csrfToken}});
  assert.ok(r.ok(), `${method} ${endpoint}: ${r.status()} ${await r.text()}`);
  return r.status() === 204 ? null : r.json();
}
async function seed() {
  for (const s of (await state()).slots) await change('DELETE', `${avail}/slots/${s.slotId}`);
  await change('PUT', `${avail}/days`, {days: [1, 3, 5]});
  await change('POST', `${avail}/slots`, {dayOfWeek: 1, startTime: '09:00', endTime: '11:00'});
  await change('POST', `${avail}/slots`, {dayOfWeek: 3, startTime: '13:00', endTime: '15:00'});
}
async function open({auth = true, init, route, url = '/availability.html'} = {}) {
  const c = await browser.newContext({...(auth ? {httpCredentials: credentials} : {}), viewport: {width: 1440, height: 1000}, locale: 'en-US', timezoneId: 'Pacific/Auckland'});
  if (init) await c.addInitScript(init);
  if (route) await route(c);
  const p = await c.newPage(); p.on('pageerror', e => result.errors.push(e.message));
  if (auth) await p.goto(root + '/api/match-session');
  await p.goto(root + url, {waitUntil: 'domcontentloaded'});
  return {c, p};
}
async function loaded(p) { await p.locator('#slot-save:not(:disabled)').waitFor(); }
const day = (p, n) => p.locator('.day-choice').filter({has: p.locator(`input[value="${n}"]`)});
async function fillSlot(p, n = '5', start = '10:00', end = '12:00') {
  await p.locator('#slot-day').selectOption(n); await p.locator('#slot-start').fill(start); await p.locator('#slot-end').fill(end);
}
async function submitLogin(p) {
  await p.locator('#email').fill('review@example.test'); await p.locator('#password').fill('browser-test-placeholder');
  await p.locator('.login-submit').click();
}
async function check(name, fn) { await fn(); result.checks.push(name); console.log('PASS ' + name); }
(async () => {
  browser = await chromium.launch({...(process.env.SPARKZ_CHROMIUM ? {executablePath: process.env.SPARKZ_CHROMIUM} : {}), headless: true, args: ['--no-sandbox']});
  api = await request.newContext({baseURL: root, httpCredentials: credentials});
  const session = await api.get('/api/match-session'); assert.equal(session.status(), 200, 'The local-only demo helper must be available.');
  csrf = await session.json(); assert.equal(csrf.userId, user); assert.ok(csrf.csrfHeader && csrf.csrfToken);
  await check('Delayed initial read blocks all writes and preserves existing slots', async () => {
    await seed(); let release, captured, writes = 0;
    const gate = new Promise(r => release = r), seen = new Promise(r => captured = r);
    const {c, p} = await open({route: async c => { await c.route('**/api/profiles/me/availability', async r => {
      // route.fetch uses a separate HTTP client; forward the disposable actor's identity.
      const response = await r.fetch({headers: {...await r.request().allHeaders(),
        Authorization: 'Basic ' + Buffer.from(`${credentials.username}:${credentials.password}`).toString('base64')}});
      const body = await response.body(); captured(); await gate; await r.fulfill({response, body});
    }); }});
    try {
      p.on('request', r => { if (r.method() !== 'GET' && r.url().includes(avail)) writes++; }); await seen;
      assert.equal(await p.getByRole('button', {name: 'Save my days'}).isDisabled(), true);
      assert.equal(await p.locator('#slot-save').isDisabled(), true);
      await p.evaluate(() => { document.querySelector('.day-choice input[value="3"]').checked = true; document.querySelector('#days-form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true})); });
      await delay(); assert.equal(writes, 0); assert.equal((await state()).slots.length, 2);
      release(); await loaded(p); assert.equal(await p.locator('.day-choice input:checked').count(), 3);
      assert.ok((await p.locator('#availability-time-zone').textContent()).includes('America/Chicago'));
    } finally { release(); await c.close(); }
  });
  await check('Failed or malformed initial read requires a successful retry before editing', async () => {
    for (const malformed of [false, true]) {
      let fail = true; const {c, p} = await open({route: async c => { await c.route('**/api/profiles/me/availability', r => fail
        ? r.fulfill({status: malformed ? 200 : 503, contentType: 'application/json', body: malformed ? '{"days":null,"slots":[]}' : '{"detail":"Temporarily unavailable"}'}) : r.continue()); }});
      try {
        await p.getByRole('button', {name: 'Retry loading'}).waitFor(); assert.equal(await p.locator('#slot-save').isDisabled(), true);
        fail = false; await p.getByRole('button', {name: 'Retry loading'}).click(); await loaded(p); assert.equal(await p.locator('.slot-item').count(), 2);
      } finally { await c.close(); }
    }
  });
  await check('Deleting days confirms and clears only deleted active edits; valid drafts survive', async () => {
    await seed(); const {c, p} = await open();
    try {
      await loaded(p); await p.locator('.slot-item').filter({hasText: 'Monday'}).getByRole('button', {name: 'Edit', exact: true}).click();
      const id = await p.locator('#slot-id').inputValue(); await p.locator('#slot-start').fill('08:00');
      await p.getByRole('button', {name: 'Save my days'}).click(); await p.locator('#days-message').filter({hasText: 'have been saved'}).waitFor();
      assert.equal(await p.locator('#slot-id').inputValue(), id); assert.equal(await p.locator('#slot-start').inputValue(), '08:00'); assert.equal(await p.locator('#slot-day').inputValue(), '1');
      await day(p, 1).click(); p.once('dialog', d => d.dismiss()); await p.getByRole('button', {name: 'Save my days'}).click(); assert.ok((await state()).days.includes(1));
      p.once('dialog', d => d.accept()); await p.getByRole('button', {name: 'Save my days'}).click(); await p.locator('#days-message').filter({hasText: 'have been saved'}).waitFor();
      assert.equal(await p.locator('#slot-id').inputValue(), ''); assert.ok((await p.locator('#slot-save').textContent()).includes('Add time slot'));
      await fillSlot(p, '3', '10:00', '11:00'); await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'Time slot added.'}).waitFor(); assert.equal((await state()).slots.length, 2);
      const row = p.locator('.slot-item').first(); await row.getByRole('button', {name: 'Edit', exact: true}).click();
      p.once('dialog', d => d.accept()); await row.getByRole('button', {name: 'Remove', exact: true}).click(); await p.locator('#slot-message').filter({hasText: 'Time slot removed.'}).waitFor(); assert.equal(await p.locator('#slot-id').inputValue(), '');
    } finally { await c.close(); }
  });
  await check('A slot deleted in another context refreshes after 404 and permits a new slot', async () => {
    await seed(); const {c, p} = await open();
    try {
      await loaded(p); await p.locator('.slot-item').filter({hasText: 'Monday'}).getByRole('button', {name: 'Edit', exact: true}).click();
      const id = await p.locator('#slot-id').inputValue(); await change('DELETE', `${avail}/slots/${id}`);
      await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'has been refreshed'}).waitFor(); assert.equal(await p.locator('#slot-id').inputValue(), '');
      await fillSlot(p, '1', '09:00', '11:00'); await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'Time slot added.'}).waitFor(); assert.equal((await state()).slots.length, 2);
    } finally { await c.close(); }
  });
  await check('One in-flight mutation locks every form and blocks duplicate submissions', async () => {
    await seed(); let release, captured, posts = 0, dayWrites = 0;
    const gate = new Promise(r => release = r), seen = new Promise(r => captured = r);
    const {c, p} = await open({route: async c => { await c.route('**/api/profiles/me/availability/slots', async r => {
      if (r.request().method() === 'POST') { posts++; captured(); await gate; } await r.continue();
    }); }});
    try {
      await loaded(p); p.on('request', r => { if (r.method() === 'PUT' && r.url().endsWith('/availability/days')) dayWrites++; });
      await fillSlot(p); await p.locator('#slot-save').click(); await seen;
      assert.equal(await p.getByRole('button', {name: 'Save my days'}).isDisabled(), true); assert.equal(await p.locator('.slot-actions button').first().isDisabled(), true);
      await p.evaluate(() => { for (const id of ['slot-form', 'slot-form', 'days-form']) document.getElementById(id).dispatchEvent(new Event('submit', {bubbles: true, cancelable: true})); });
      await delay(); assert.equal(posts, 1); assert.equal(dayWrites, 0); release();
      await p.locator('#slot-message').filter({hasText: 'Time slot added.'}).waitFor(); assert.equal((await state()).slots.length, 3);
    } finally { release(); await c.close(); }
  });
  await check('A saved write followed by refresh failure is not reported as an unsaved write', async () => {
    await seed(); const {c, p} = await open(); let fail = true, posts = 0;
    try {
      await loaded(p); p.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/availability/slots')) posts++; });
      await c.route('**/api/profiles/me/availability', r => fail ? r.fulfill({status: 503, contentType: 'application/json', body: '{}'}) : r.continue());
      await fillSlot(p); await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'Your change was saved'}).waitFor();
      assert.equal((await state()).slots.length, 3); assert.equal(await p.locator('#slot-save').isDisabled(), true);
      await p.evaluate(() => document.querySelector('#slot-form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}))); await delay(); assert.equal(posts, 1);
      fail = false; await p.getByRole('button', {name: 'Retry loading'}).click(); await loaded(p); assert.equal(await p.locator('.slot-item').count(), 3); assert.equal(posts, 1);
    } finally { await c.close(); }
  });
  await check('Invalid local times and rejected server validation preserve an editable form', async () => {
    await seed(); const {c, p} = await open(); let reject = true, posts = 0;
    try {
      await loaded(p); await c.route('**/api/profiles/me/availability/slots', r => {
        if (r.request().method() === 'POST') { posts++; if (reject) return r.fulfill({status: 400, contentType: 'application/json', body: '{"detail":"Choose a valid time window"}'}); }
        return r.continue();
      });
      await fillSlot(p, '5', '12:00', '10:00'); await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'end time must be later'}).waitFor(); assert.equal(posts, 0);
      await p.locator('#slot-end').fill('13:00'); await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'Choose a valid time window'}).waitFor(); assert.equal(await p.locator('#slot-start').inputValue(), '12:00'); assert.equal(await p.locator('#slot-save').isEnabled(), true);
      reject = false; await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'Time slot added.'}).waitFor();
    } finally { await c.close(); }
  });
  await check('Anonymous access cannot mutate availability; demo changes stay in demo storage', async () => {
    const anon = await open({auth: false}); try {
      await anon.p.getByRole('button', {name: 'Retry loading'}).waitFor(); assert.equal(await anon.p.locator('#slot-save').isDisabled(), true);
    } finally { await anon.c.close(); }
    const {c, p} = await open({auth: false, url: '/availability.html?demo=1'}); let writes = 0;
    try {
      p.on('request', r => { if (r.url().includes(avail) && r.method() !== 'GET') writes++; });
      await loaded(p); assert.equal(await p.locator('.slot-item').count(), 3); await fillSlot(p, '1', '15:00', '16:00'); await p.locator('#slot-save').click(); await p.locator('#slot-message').filter({hasText: 'Time slot added.'}).waitFor();
      await p.reload(); await loaded(p); assert.equal(await p.locator('.slot-item').count(), 4); assert.equal(writes, 0);
    } finally { await c.close(); }
  });
  await check('Login without a provider or without a verified identity never navigates to Browse', async () => {
    for (const resolveWithoutIdentity of [false, true]) {
      const {c, p} = await open({auth: false, url: '/login.html', ...(resolveWithoutIdentity ? {init: () => { window.sparkzAuth = {signIn: async () => {}, getAccessToken: async () => null}; }} : {})});
      try { await submitLogin(p); await p.locator('#login-message').filter({hasText: resolveWithoutIdentity ? 'has not completed' : 'not available yet'}).waitFor(); assert.equal(p.url(), root + '/login.html'); assert.equal(await p.locator('.login-submit').isEnabled(), true); }
      finally { await c.close(); }
    }
  });
  await check('Provider errors and failed verification remain retryable on the Login page', async () => {
    const denied = await open({auth: false, url: '/login.html', init: () => { window.sparkzAuth = {signIn: async () => ({error: {message: 'Provider declined sign-in'}})}; }});
    try { await submitLogin(denied.p); await denied.p.locator('#login-message').filter({hasText: 'Provider declined'}).waitFor(); assert.equal(await denied.p.locator('.login-submit').isEnabled(), true); } finally { await denied.c.close(); }
    const {c, p} = await open({auth: false, url: '/login.html', init: () => { window.sparkzAuth = {signIn: async () => {}, getAccessToken: async () => 'browser-test-token'}; }, route: async c => { await c.route('**/api/profiles/me', r => r.fulfill({status: 503, contentType: 'application/json', body: '{}'})); }});
    try { await submitLogin(p); await p.locator('#login-message').filter({hasText: 'could not be verified'}).waitFor(); assert.equal(p.url(), root + '/login.html'); assert.equal(await p.locator('.login-submit').isEnabled(), true); } finally { await c.close(); }
  });
  await check('Verified local returning users reach Browse; duplicate provider calls are blocked', async () => {
    const {c, p} = await open({url: '/login.html', init: () => { window.loginAttempts = 0; window.sparkzAuth = {signIn: () => { window.loginAttempts++; return new Promise(r => { window.finishSignIn = r; }); }, getAccessToken: async () => null}; }});
    try {
      await submitLogin(p); await p.evaluate(() => document.querySelector('#login-form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}))); assert.equal(await p.evaluate(() => window.loginAttempts), 1);
      await p.evaluate(() => window.finishSignIn()); await p.waitForURL('**/browse.html'); await p.locator('.account-toggle').waitFor();
    } finally { await c.close(); }
  });
  await check('Authenticated users awaiting a profile or onboarding return Home safely', async () => {
    for (const missing of [false, true]) {
      let bearer = false; const {c, p} = await open({auth: false, url: '/login.html', init: () => { window.sparkzAuth = {signIn: async () => {}, getAccessToken: async () => 'browser-test-token'}; }, route: async c => { await c.route('**/api/profiles/me', r => {
        bearer = r.request().headers().authorization === 'Bearer browser-test-token';
        return r.fulfill({status: missing ? 404 : 200, contentType: 'application/json', body: JSON.stringify(missing ? {} : {userId: user, username: 'New member', onboardingCompleted: false})});
      }); }});
      try { await submitLogin(p); await p.waitForURL(root + '/'); assert.equal(bearer, true); await p.locator('.account-toggle').waitFor(); } finally { await c.close(); }
    }
  });
  await check('Approved primary tabs, profile menu and responsive availability layout remain intact', async () => {
    await seed(); const {c, p} = await open();
    try {
      await loaded(p); assert.deepEqual(await p.locator('.primary-nav > *').allTextContents(), ['Home', 'Browse', 'Match requests', 'Sessions']);
      for (const width of [1440, 1280, 1100, 1024, 1000, 900, 768, 600, 480, 390, 375, 320]) {
        await p.setViewportSize({width, height: 1000}); const m = await p.evaluate(() => ({width: innerWidth, scrollWidth: document.documentElement.scrollWidth})); result.viewports.push(m); assert.ok(m.scrollWidth <= width, 'Overflow at ' + width);
        if (width === 390) await p.screenshot({path: path.join(output, 'availability-mobile.png'), fullPage: true});
      }
      await p.setViewportSize({width: 1440, height: 1100}); await p.screenshot({path: path.join(output, 'availability-desktop.png'), fullPage: true});
      await p.getByRole('button', {name: 'Profile menu', exact: true}).click(); await p.getByRole('menuitem', {name: 'Manage availability', exact: true}).waitFor();
    } finally { await c.close(); }
  });
  assert.deepEqual(result.errors, []); console.log(`Completed ${result.checks.length} frontend regression checks.`);
})().catch(error => { result.failure = error.stack; console.error(error); process.exitCode = 1; }).finally(async () => {
  fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(result, null, 2)); if (api) await api.dispose(); if (browser) await browser.close();
});
