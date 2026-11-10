/* Integration point for #5: define window.sparkzAuth.getAccessToken() before this
   script executes. Return the current Supabase access token; never a user ID.
   Local demo uses a server-authenticated Basic session and CSRF tokens instead. */
(() => {
  let direction = 'incoming';
  let session;
  let revision = 0;
  const byId = id => document.getElementById(id);
  const message = (text, error = false) => {
    byId('message').textContent = text;
    byId('message').className = error ? 'error' : '';
  };
  async function api(path, options = {}) {
    const headers = { Accept: 'application/json', ...options.headers };
    const token = await window.sparkzAuth?.getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    else {
      if (!session) {
        const response = await fetch('/api/match-session');
        if (!response.ok) throw new Error('Please sign in to manage match requests.');
        session = await response.json();
      }
      if (options.method) headers[session.csrfHeader] = session.csrfToken;
    }
    if (options.body) headers['Content-Type'] = 'application/json';
    const response = await fetch(path, { ...options, headers });
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (_) { /* May be an authentication response. */ }
      throw new Error(problem.detail || `Request failed (${response.status}). Please refresh and try again.`);
    }
    return response.json();
  }
  function element(tag, text, className) {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  async function load() {
    const current = ++revision;
    const selected = direction;
    try {
      const rows = await api(`/api/match-requests?direction=${selected}`);
      if (current !== revision) return;
      byId('requests').replaceChildren();
      if (!rows.length) byId('requests').append(element('p', `No ${selected} requests yet.`, 'empty'));
      for (const row of rows) {
        const card = element('article', '', 'request');
        const info = element('div', '');
        info.append(element('strong', `Skill #${row.skillId}`), element('p', selected === 'incoming' ? `From ${row.senderId}` : `To ${row.receiverId}`), element('p', new Date(row.createdAt).toLocaleString()), element('span', row.status, 'status'));
        card.append(info);
        if (row.status === 'Pending') {
          const actions = element('div', '', 'actions');
          for (const status of selected === 'incoming' ? ['Accepted', 'Declined'] : ['Canceled']) {
            const button = element('button', {Accepted:'Accept', Declined:'Decline', Canceled:'Cancel'}[status]);
            button.addEventListener('click', async () => {
              actions.querySelectorAll('button').forEach(b => b.disabled = true);
              try {
                await api(`/api/match-requests/${row.id}`, {method:'PATCH', body:JSON.stringify({status})});
                message(`Request ${status.toLowerCase()}.`);
                await load();
              } catch (e) { message(e.message, true); }
              finally { actions.querySelectorAll('button').forEach(b => b.disabled = false); }
            });
            actions.append(button);
          }
          card.append(actions);
        }
        byId('requests').append(card);
      }
    } catch (e) { if (current === revision) message(e.message, true); }
  }
  for (const tab of ['incoming', 'outgoing']) byId(tab).addEventListener('click', () => {
    direction = tab;
    for (const id of ['incoming', 'outgoing']) byId(id).setAttribute('aria-pressed', String(id === tab));
    load();
  });
  byId('refresh').addEventListener('click', load);
  // #7 may link to /matches.html?receiverId=<uuid>&skillId=<bigint>.
  const query = new URLSearchParams(location.search);
  byId('receiver').value = query.get('receiverId') || '';
  byId('skill').value = query.get('skillId') || '';
  byId('send-form').addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.target.querySelector('button');
    button.disabled = true;
    try {
      const skillId = Number(byId('skill').value);
      if (!Number.isSafeInteger(skillId) || skillId < 1) throw new Error('Enter a valid skill ID.');
      await api('/api/match-requests', { method:'POST', body:JSON.stringify({receiverId:byId('receiver').value.trim(), skillId}) });
      message('Request sent.');
      byId('outgoing').click();
    } catch (e) { message(e.message, true); }
    finally { button.disabled = false; }
  });
  load();
})();
