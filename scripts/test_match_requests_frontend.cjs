/* Disposable local-profile H2 only. See docs/match-requests.md. */
const {chromium, request} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const target = new URL(process.env.SPARKZ_BASE_URL || 'http://127.0.0.1:8080');
assert.ok(target.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname), 'Use an isolated loopback local-profile app.');
const root = target.origin;
const A = '00000000-0000-0000-0000-000000000001', B = '00000000-0000-0000-0000-000000000002', C = '00000000-0000-0000-0000-000000000003';
const password = process.env.SPARKZ_LOCAL_PASSWORD || 'local-demo';
const output = process.env.SPARKZ_BROWSER_ARTIFACTS || '/tmp/sparkz-matches-browser';
fs.mkdirSync(output, {recursive: true});
const result = {checks: [], pageErrors: [], viewports: []}, clients = new Map();
let browser;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const json = (route, data, status = 200) => route.fulfill({status, contentType: 'application/json', body: JSON.stringify(data)});
const connection = (id = C, skill = 2) => `/matches.html?${new URLSearchParams({receiverId: id, skillId: skill})}`;
async function client(id) {
  if (!clients.has(id)) {
    const api = await request.newContext({baseURL: root, httpCredentials: {username: id, password}});
    const session = await api.get('/api/match-session'); assert.equal(session.status(), 200);
    const csrf = await session.json(); assert.equal(csrf.userId, id);
    clients.set(id, {api, csrf});
  }
  return clients.get(id);
}
async function read(id, url) { const {api} = await client(id); const r = await api.get(url); assert.equal(r.status(), 200); return r.json(); }
async function write(id, method, url, data) {
  const {api, csrf} = await client(id); const r = await api.fetch(url, {method, data, headers: {[csrf.csrfHeader]: csrf.csrfToken}});
  assert.ok(r.ok(), `${method} ${url}: ${r.status()} ${await r.text()}`); return r.json();
}
async function cancelPending(id) {
  for (const row of await read(id, '/api/match-requests?direction=outgoing')) if (row.status === 'Pending') await write(id, 'PATCH', `/api/match-requests/${row.id}`, {status: 'Canceled'});
}
async function open({id = A, url = connection(), init, route, auth = true} = {}) {
  const c = await browser.newContext({...(auth ? {httpCredentials: {username: id, password}} : {}), viewport: {width: 1440, height: 1000}, locale: 'en-US', timezoneId: 'America/Chicago'});
  if (init) await c.addInitScript(init);
  if (route) await route(c);
  const p = await c.newPage(); p.on('pageerror', e => result.pageErrors.push(e.message));
  p.setDefaultTimeout(10000);
  if (auth) await p.goto(root + '/api/match-session');
  await p.goto(root + url, {waitUntil: 'domcontentloaded'});
  return {c, p};
}
const loaded = p => p.locator('#requests[aria-busy="false"]').waitFor();
const ready = p => p.locator('#send-request:not(:disabled)').waitFor();
const card = (p, id) => p.locator(`[data-request-id="${id}"]`);
async function check(name, fn) { await fn(); result.checks.push(name); console.log('PASS ' + name); }
(async () => {
  browser = await chromium.launch({...(process.env.SPARKZ_CHROMIUM ? {executablePath: process.env.SPARKZ_CHROMIUM} : {}), headless: true, args: ['--no-sandbox']});
  assert.equal((await read(A, `/api/discovery/${C}`)).username, 'Maya Chen', 'Load the optional discovery-demo-data.sql fixture.');
  // This suite intentionally finishes with an accepted pair: restart H2 before rerunning.
  assert.equal((await read(A, `/api/discovery/${B}`)).matchStatus, null, 'Use a fresh, disposable local H2 database.');
  await cancelPending(A); await cancelPending(B);
  await check('Browse Connect carries its selected person and compatible skill; no ID inputs', async () => {
    const {c, p} = await open({url: '/browse.html'});
    try {
      await p.locator('#people[aria-busy="false"]').waitFor();
      await p.locator('#skill').selectOption('3'); await p.locator('#filters').evaluate(form => form.requestSubmit());
      await p.locator('#people[aria-busy="false"]').waitFor();
      const person = p.locator('.person').filter({has: p.getByRole('heading', {name: 'Maya Chen', exact: true})});
      await person.locator('.connect-link').waitFor();
      assert.ok((await person.locator('.connect-link').getAttribute('href')).endsWith('skillId=3'));
      await person.locator('.connect-link').click(); await ready(p); await loaded(p);
      assert.equal(await p.locator('#connection-name').textContent(), 'Maya Chen');
      assert.equal(await p.locator('#connection-school').textContent(), 'Demo University');
      assert.equal(await p.locator('#skill').inputValue(), '3');
      assert.equal(await p.locator('main input').count(), 0);
      assert.deepEqual(await p.locator('#skill option:not([value=""])').allTextContents(), ['Guitar', 'Python']);
      await p.locator('#skill').selectOption('2');
      await p.locator('#connection-reason').filter({hasText: 'learn Guitar from Maya Chen'}).waitFor();
      await p.locator('.account-toggle:visible').waitFor(); await p.evaluate(() => document.fonts.ready);
      await p.screenshot({path: path.join(output, 'matches-desktop.png'), fullPage: true});
      for (const width of [320, 375, 390, 600, 768, 1000, 1024, 1280, 1440]) {
        await p.setViewportSize({width, height: 900});
        assert.equal(await p.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `Overflow at ${width}`);
        if (width <= 1000) { await p.locator('.nav-toggle').click(); assert.equal(await p.locator('.primary-nav').isVisible(), true); await p.locator('.nav-toggle').click(); }
        result.viewports.push(width);
      }
      await p.setViewportSize({width: 390, height: 844}); await p.screenshot({path: path.join(output, 'matches-mobile.png'), fullPage: true});
      assert.deepEqual(await p.locator('.primary-nav > *').allTextContents(), ['Home', 'Browse', 'Match requests', 'Sessions']);
    } finally { await c.close(); }
  });
  await check('One in-flight submission sends verified IDs internally and renders names; Cancel works', async () => {
    let release, seen, posts = 0, payload;
    const gate = new Promise(r => release = r), captured = new Promise(r => seen = r);
    const {c, p} = await open({route: async c => c.route('**/api/match-requests', async route => {
      if (route.request().method() === 'POST') { posts++; payload = route.request().postDataJSON(); seen(); await gate; }
      return route.continue();
    })});
    try {
      await ready(p); await p.locator('#skill').selectOption('3'); await p.locator('#send-request').click(); await captured;
      assert.equal(await p.locator('#send-request').isDisabled(), true); assert.equal(await p.locator('#skill').isDisabled(), true);
      await p.locator('#send-form').evaluate(form => { form.dispatchEvent(new Event('submit', {cancelable: true})); form.dispatchEvent(new Event('submit', {cancelable: true})); });
      await delay(100); assert.equal(posts, 1); assert.deepEqual(payload, {receiverId: C, skillId: 3}); release();
      await p.locator('#message').filter({hasText: 'Request sent.'}).waitFor(); await loaded(p);
      assert.equal(await p.locator('#outgoing').getAttribute('aria-pressed'), 'true');
      const row = (await read(A, '/api/match-requests?direction=outgoing')).find(r => r.receiverId === C && r.status === 'Pending');
      assert.ok(row); await card(p, row.id).getByRole('heading', {name: 'To Maya Chen'}).waitFor();
      assert.ok((await card(p, row.id).innerText()).includes('Python'));
      assert.equal((await p.locator('#requests').innerText()).includes(C), false);
      assert.equal(await p.locator('#send-request').textContent(), 'Request pending');
      await p.setViewportSize({width: 1440, height: 1000}); await p.screenshot({path: path.join(output, 'matches-outgoing.png'), fullPage: true});
      await card(p, row.id).getByRole('button', {name: 'Cancel', exact: true}).click();
      await card(p, row.id).locator('.status').filter({hasText: 'Canceled'}).waitFor(); await ready(p);
    } finally { release(); await c.close(); await cancelPending(A); }
  });
  await check('Direct, malformed, self and missing targets guide back to Browse; stale skill cannot submit', async () => {
    for (const url of ['/matches.html', '/matches.html?receiverId=invalid', connection(A), connection('00000000-0000-0000-0000-000000000099')]) {
      const {c, p} = await open({url});
      try {
        await loaded(p); await p.locator('#connection-state').filter({hasText: /Choose someone|no longer available/}).waitFor();
        assert.equal(await p.locator('#send-form').isHidden(), true);
        assert.equal(await p.getByRole('link', {name: 'Browse people'}).getAttribute('href'), '/browse.html');
      } finally { await c.close(); }
    }
    const {c, p} = await open({url: connection(C, 999)});
    try {
      await p.locator('#send-form:visible').waitFor(); assert.equal(await p.locator('#skill').inputValue(), '');
      assert.equal(await p.locator('#send-request').isDisabled(), true);
      assert.ok((await p.locator('#connection-reason').innerText()).includes('no longer available'));
      await p.locator('#skill').selectOption('2'); await ready(p);
    } finally { await c.close(); }
  });
  await check('Existing request from another tab returns 409 and disables duplicate sending', async () => {
    const {c, p} = await open();
    try {
      await ready(p); await write(A, 'POST', '/api/match-requests', {receiverId: C, skillId: 2});
      await p.locator('#send-request').click();
      await p.locator('#send-request').filter({hasText: 'Request pending'}).waitFor();
      assert.equal(await p.locator('#send-request').isDisabled(), true);
      assert.equal((await read(A, '/api/match-requests?direction=outgoing')).filter(r => r.receiverId === C && r.status === 'Pending').length, 1);
    } finally { await c.close(); await cancelPending(A); }
  });
  await check('Committed send with lost response is checked before resending; refresh failure preserves success', async () => {
    for (const lostResponse of [true, false]) {
      let posted = false;
      const {c, p} = await open({route: async c => c.route('**/api/match-requests**', async route => {
        if (route.request().method() === 'POST') {
          posted = true;
          if (lostResponse) {
            const response = await route.fetch({headers: {...await route.request().allHeaders(), Authorization: 'Basic ' + Buffer.from(`${A}:${password}`).toString('base64')}});
            assert.equal(response.status(), 201); return route.abort('failed');
          }
        } else if (posted && !lostResponse) return json(route, {detail: 'Refresh unavailable'}, 503);
        return route.continue();
      })});
      try {
        await ready(p); await p.locator('#send-request').click();
        if (lostResponse) {
          await p.getByRole('button', {name: 'Check request status'}).waitFor(); assert.equal(await p.locator('#send-request').isDisabled(), true);
          await p.getByRole('button', {name: 'Check request status'}).click();
          await p.locator('#send-request').filter({hasText: 'Request pending'}).waitFor();
        } else {
          await p.locator('#message').filter({hasText: 'Request sent. Refresh the list to view it.'}).waitFor();
          assert.equal(await p.locator('#send-request').isDisabled(), true);
        }
        assert.equal((await read(A, '/api/match-requests?direction=outgoing')).filter(r => r.receiverId === C && r.status === 'Pending').length, 1);
      } finally { await c.close(); await cancelPending(A); }
    }
  });
  await check('Failed profile read retries; changed eligibility after 422 removes invalid skill', async () => {
    let fail = true, changed = false;
    const original = await read(A, `/api/discovery/${C}`);
    const {c, p} = await open({route: async c => {
      await c.route(`**/api/discovery/${C}`, route => fail ? json(route, {detail: 'Profile unavailable for now'}, 503) : changed
        ? json(route, {...original, canLearn: [], canTeach: []}) : route.continue());
      await c.route('**/api/match-requests', route => { if (route.request().method() === 'POST') { changed = true; return json(route, {detail: 'This skill is no longer compatible'}, 422); } return route.continue(); });
    }});
    try {
      await p.getByRole('button', {name: 'Try again'}).waitFor(); assert.equal(await p.locator('#send-form').isHidden(), true);
      fail = false; await p.getByRole('button', {name: 'Try again'}).click(); await ready(p);
      await p.locator('#send-request').click(); await p.locator('#connection-state').filter({hasText: 'do not currently share a compatible'}).waitFor();
      assert.equal(await p.locator('#send-request').isDisabled(), true); assert.equal(await p.locator('#skill option:not([value=""])').count(), 0);
    } finally { await c.close(); }
  });
  await check('Partial details protect unavailable partners; Incoming/Outgoing race keeps the latest tab', async () => {
    let release, seen;
    const gate = new Promise(r => release = r), captured = new Promise(r => seen = r);
    const row = {id: 9001, senderId: B, receiverId: A, skillId: 1, status: 'Pending', createdAt: '2026-11-13T10:00:00Z'};
    const {c, p} = await open({url: '/matches.html', route: async c => {
      await c.route('**/api/match-requests?*', async route => {
        if (new URL(route.request().url()).searchParams.get('direction') === 'incoming') { seen(); await gate; return json(route, [row]); }
        return json(route, []);
      });
      await c.route(`**/api/discovery/${B}`, route => json(route, {}, 404));
    }});
    try {
      await captured; await p.locator('#outgoing').click(); await loaded(p); release(); await delay(150);
      assert.equal(await p.locator('#outgoing').getAttribute('aria-pressed'), 'true'); assert.equal(await p.locator('.request').count(), 0);
      await p.locator('#incoming').click(); await loaded(p);
      await p.getByRole('heading', {name: 'From a Sparkz member'}).waitFor();
      assert.equal(await p.getByRole('button', {name: 'Accept', exact: true}).isDisabled(), true);
      assert.equal(await p.getByRole('button', {name: 'Decline', exact: true}).isEnabled(), true);
      assert.equal((await p.locator('#requests').innerText()).includes(B), false);
    } finally { release(); await c.close(); }
  });
  await check('Bearer auth sends no local helper request; untrusted names stay text and fit mobile', async () => {
    let helperCalls = 0, payload, authorization;
    const original = await read(A, `/api/discovery/${C}`), injected = '<img src=x onerror="window.injected=true">';
    const {c, p} = await open({auth: false, init: () => { window.sparkzAuth = {getAccessToken: async () => 'browser-test-token'}; }, route: async c => {
      await c.route('**/api/**', route => {
        const endpoint = new URL(route.request().url()).pathname;
        if (endpoint === '/api/match-session') { helperCalls++; return json(route, {}, 404); }
        authorization = route.request().headers().authorization;
        if (endpoint === '/api/profiles/me') return json(route, {userId: A, username: 'Alex', onboardingCompleted: true});
        if (endpoint === `/api/discovery/${C}`) return json(route, {...original, username: injected, schoolName: injected, canLearn: [{skillId: 2, skillName: injected.repeat(8)}], canTeach: []});
        if (endpoint === '/api/skills') return json(route, [{skillId: 2, skillName: injected}]);
        if (endpoint === '/api/match-requests') {
          if (route.request().method() === 'POST') { payload = route.request().postDataJSON(); return json(route, {id: 9002}, 201); }
          return json(route, []);
        }
        return route.continue();
      });
    }});
    try {
      await ready(p); assert.equal(await p.locator('#connection-name').textContent(), injected);
      assert.equal(await p.locator('#send-form img').count(), 0); assert.equal(await p.evaluate(() => Boolean(window.injected)), false);
      await p.setViewportSize({width: 320, height: 800}); assert.equal(await p.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
      await p.locator('#send-request').click(); await p.locator('#message').filter({hasText: 'Request sent.'}).waitFor();
      assert.deepEqual(payload, {receiverId: C, skillId: 2}); assert.equal(authorization, 'Bearer browser-test-token'); assert.equal(helperCalls, 0);
    } finally { await c.close(); }
  });
  await check('Two real accounts send, decline, then accept without displaying IDs; accepted pair cannot resend', async () => {
    for (const status of ['Declined', 'Accepted']) {
      const sender = await open({url: connection(B, 1)}); let recipient;
      try {
        await ready(sender.p); await sender.p.locator('#send-request').click(); await sender.p.locator('#message').filter({hasText: 'Request sent.'}).waitFor();
        const row = (await read(A, '/api/match-requests?direction=outgoing')).find(r => r.receiverId === B && r.status === 'Pending'); assert.ok(row);
        recipient = await open({id: B, url: '/matches.html'}); await loaded(recipient.p);
        await card(recipient.p, row.id).getByRole('heading', {name: 'From Alex'}).waitFor();
        assert.ok((await card(recipient.p, row.id).innerText()).includes('Java'));
        await card(recipient.p, row.id).getByRole('button', {name: status === 'Accepted' ? 'Accept' : 'Decline', exact: true}).click();
        await card(recipient.p, row.id).locator('.status').filter({hasText: status}).waitFor();
        await sender.p.reload();
        if (status === 'Accepted') {
          await sender.p.locator('#send-request').filter({hasText: 'Already connected'}).waitFor();
          assert.equal(await sender.p.locator('#send-request').isDisabled(), true);
          await sender.p.locator('#outgoing').click(); await loaded(sender.p);
          assert.equal(await sender.p.getByRole('link', {name: 'Schedule a session'}).count(), 0, 'Do not link to the unmerged Sessions page.');
          await sender.p.evaluate(() => document.body.setAttribute('data-session-planner', ''));
          await sender.p.locator('#refresh').click(); await loaded(sender.p);
          assert.equal(await card(sender.p, row.id).getByRole('link', {name: 'Schedule a session'}).getAttribute('href'), `/sessions.html?matchRequestId=${row.id}`);
        } else await ready(sender.p);
      } finally { await sender.c.close(); if (recipient) await recipient.c.close(); }
    }
  });
  assert.deepEqual(result.pageErrors, []); console.log(`${result.checks.length} scenario groups passed; ${result.viewports.length} viewport widths.`);
})().catch(error => { result.failure = error.stack; console.error(error); process.exitCode = 1; }).finally(async () => {
  fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(result, null, 2));
  for (const {api} of clients.values()) await api.dispose();
  if (browser) await browser.close();
});
