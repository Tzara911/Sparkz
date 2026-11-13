/* #5 supplies sparkzAuth; local demos use the authenticated Basic/CSRF helper. */
(() => {
  const byId = id => document.getElementById(id);
  const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
  const positiveId = value => /^\d+$/.test(String(value)) && Number.isSafeInteger(Number(value)) && Number(value) > 0;
  const query = new URLSearchParams(location.search);
  const receiver = uuid(query.get('receiverId')) ? query.get('receiverId').toLowerCase() : null;
  const requestedSkill = query.get('skillId');
  let direction = 'incoming', listRevision = 0, connectionRevision = 0, authRevision = 0;
  let sessionPromise, catalogPromise, listController, internalAuthEvent = false;
  let partner = null, skills = new Map(), pairStatus = null, sending = false, loadingPartner = false, needsCheck = false, selectionWarning = '';
  const pendingActions = new Set();
  const message = (text, error = false) => { byId('message').textContent = text; byId('message').className = error ? 'error' : ''; };
  function element(tag, text = '', className) {
    const node = document.createElement(tag); node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  const nameOf = person => [person?.username, person?.firstName].find(value => typeof value === 'string' && value.trim())?.trim() || 'Sparkz member';
  const initials = name => name.trim().split(/\s+/).slice(0, 2).map(word => Array.from(word)[0] || '').join('').toLocaleUpperCase();
  async function localSession() {
    if (!sessionPromise) sessionPromise = (async () => {
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 10000);
      try {
        const response = await fetch('/api/match-session', {signal: controller.signal, cache: 'no-store'});
        if (!response.ok) throw Object.assign(new Error('Please sign in to manage match requests.'), {status: response.status});
        const session = await response.json();
        internalAuthEvent = true;
        try { window.dispatchEvent(new Event('sparkz:auth-changed')); } finally { internalAuthEvent = false; }
        return session;
      } finally { clearTimeout(timeout); }
    })().catch(error => { sessionPromise = undefined; throw error; });
    return sessionPromise;
  }
  async function api(path, options = {}) {
    const controller = new AbortController(), external = options.signal;
    const epoch = authRevision;
    const abort = () => controller.abort();
    if (external?.aborted) abort(); else external?.addEventListener('abort', abort, {once: true});
    const timeout = setTimeout(abort, 10000), writing = Boolean(options.method);
    try {
      const headers = {Accept: 'application/json', ...options.headers};
      const token = await window.sparkzAuth?.getAccessToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      else { const session = await localSession(); if (writing) headers[session.csrfHeader] = session.csrfToken; }
      if (epoch !== authRevision) throw new Error('Your sign-in has changed. Please try again.');
      if (options.body) headers['Content-Type'] = 'application/json';
      let response;
      try { response = await fetch(path, {...options, headers, signal: controller.signal, cache: 'no-store'}); }
      catch (_) { throw Object.assign(new Error('The request could not be confirmed. Check your connection and refresh its status.'), {uncertain: writing}); }
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) sessionPromise = undefined;
        let problem = {}; try { problem = await response.json(); } catch (_) { /* Some auth errors have no body. */ }
        throw Object.assign(new Error(problem.detail || (response.status === 401 ? 'Please sign in to manage match requests.' : 'This request could not be completed. Please try again.')), {status: response.status, uncertain: writing && response.status >= 500});
      }
      try { return await response.json(); }
      catch (_) { throw Object.assign(new Error('The response could not be read. Refresh its status before trying again.'), {uncertain: writing}); }
    } finally { clearTimeout(timeout); external?.removeEventListener('abort', abort); }
  }
  function skillCatalog() {
    if (!catalogPromise) catalogPromise = api('/api/skills').then(rows => {
      if (!Array.isArray(rows)) throw new Error('Skill details could not be loaded.');
      return new Map(rows.filter(s => s && positiveId(s.skillId) && typeof s.skillName === 'string').map(s => [Number(s.skillId), s.skillName]));
    }).catch(error => { catalogPromise = undefined; throw error; });
    return catalogPromise;
  }
  async function person(id, signal) {
    const data = await api(`/api/discovery/${encodeURIComponent(id)}`, {signal});
    if (!data || !uuid(data.userId) || data.userId.toLowerCase() !== id.toLowerCase()
      || !Array.isArray(data.canLearn) || !Array.isArray(data.canTeach)) throw new Error('This connection could not be loaded.');
    return data;
  }
  function updateComposer(warning) {
    if (warning !== undefined) selectionWarning = warning;
    const choice = skills.get(Number(byId('skill').value));
    byId('skill').disabled = loadingPartner || sending || needsCheck || !skills.size || Boolean(pairStatus);
    byId('send-request').disabled = loadingPartner || sending || needsCheck || !partner || !choice || Boolean(pairStatus);
    byId('send-request').textContent = sending ? 'Sending…' : pairStatus === 'accepted' ? 'Already connected' : pairStatus === 'pending' ? 'Request pending' : 'Send request →';
    byId('connection-reason').textContent = pairStatus === 'accepted' ? 'You are already matched with this person. Your accepted request is listed below.'
      : pairStatus === 'pending' ? 'There is already a request between you. Check Incoming or Outgoing below.'
      : selectionWarning || (choice ? `You can ${choice.learn && choice.teach ? 'learn or share' : choice.learn ? 'learn' : 'share'} ${choice.name} ${choice.learn && !choice.teach ? 'from' : 'with'} ${nameOf(partner)}.` : 'Choose a compatible skill to continue.');
  }
  async function loadConnection(preserve = false) {
    const current = ++connectionRevision, chosen = preserve ? byId('skill').value || requestedSkill : requestedSkill;
    loadingPartner = true; partner = null; skills = new Map(); pairStatus = null; selectionWarning = '';
    byId('send-form').hidden = true; byId('connection-state').hidden = false;
    byId('connection-retry').hidden = true; byId('connection-retry').disabled = true; byId('connection-retry').textContent = 'Try again';
    byId('connection-state').textContent = receiver ? 'Loading your connection…' : 'Choose someone in Browse and click Connect to start a skill exchange.';
    updateComposer();
    if (!receiver) { loadingPartner = false; return; }
    try {
      const data = await person(receiver);
      if (current !== connectionRevision) return;
      partner = data; pairStatus = ['pending', 'accepted'].includes(data.matchStatus?.toLowerCase()) ? data.matchStatus.toLowerCase() : null;
      for (const [items, role] of [[data.canLearn, 'learn'], [data.canTeach, 'teach']]) for (const item of items) {
        if (!item || !positiveId(item.skillId) || typeof item.skillName !== 'string' || !item.skillName.trim()) continue;
        const id = Number(item.skillId), choice = skills.get(id) || {name: item.skillName, learn: false, teach: false};
        choice[role] = true; skills.set(id, choice);
      }
      byId('connection-name').textContent = nameOf(data); byId('connection-school').textContent = data.schoolName || 'School not provided';
      byId('connection-avatar').textContent = initials(nameOf(data));
      byId('skill').replaceChildren(new Option(skills.size ? 'Choose a skill' : 'No compatible skills available', ''));
      for (const [id, choice] of skills) byId('skill').add(new Option(choice.name, String(id)));
      const supplied = chosen !== null && chosen !== '';
      if (positiveId(chosen) && skills.has(Number(chosen))) byId('skill').value = String(Number(chosen));
      else if (!supplied && skills.size === 1) byId('skill').value = String(skills.keys().next().value);
      needsCheck = false; byId('send-form').hidden = false; byId('connection-state').hidden = Boolean(skills.size);
      if (!skills.size) byId('connection-state').textContent = 'You do not currently share a compatible teaching or learning skill. Browse to choose another person.';
      loadingPartner = false;
      updateComposer(supplied && !skills.has(Number(chosen)) ? 'That skill is no longer available for this connection. Choose another compatible skill.' : '');
    } catch (error) {
      if (current !== connectionRevision) return;
      byId('connection-state').textContent = error.status === 404 || error.status === 403
        ? 'This profile is no longer available for a connection. Browse to choose another person.' : error.message;
      byId('connection-retry').hidden = error.status === 404 || error.status === 403;
    } finally {
      if (current === connectionRevision) { loadingPartner = false; byId('connection-retry').disabled = false; updateComposer(); }
    }
  }
  async function partnersFor(rows, selected, signal) {
    const queue = [...new Set(rows.map(row => selected === 'incoming' ? row.senderId : row.receiverId))], people = new Map();
    await Promise.all(Array.from({length: Math.min(4, queue.length)}, async () => {
      while (queue.length && !signal.aborted) {
        const id = queue.shift(); try { people.set(id, await person(id, signal)); } catch (_) { people.set(id, null); }
      }
    }));
    return people;
  }
  async function changeStatus(row, status, actions, selected) {
    if (pendingActions.has(row.id)) return;
    const previousDisabled = new Map([...actions.querySelectorAll('button')].map(button => [button, button.disabled]));
    pendingActions.add(row.id); actions.querySelectorAll('button').forEach(button => { button.disabled = true; });
    const epoch = authRevision;
    try {
      await api(`/api/match-requests/${row.id}`, {method: 'PATCH', body: JSON.stringify({status})});
      if (epoch !== authRevision) return;
      message(`Request ${status.toLowerCase()}.`); await loadRequests();
    } catch (error) {
      if (epoch !== authRevision) return;
      message(error.status === 404 || error.status === 409 ? 'This request has changed. Refreshing its status…' : error.message, true);
      if (error.uncertain || [404, 409, 422].includes(error.status)) await loadRequests();
    } finally {
      if (epoch === authRevision) {
        pendingActions.delete(row.id); actions.querySelectorAll('button').forEach(button => { button.disabled = previousDisabled.get(button); });
        if (receiver === (selected === 'incoming' ? row.senderId : row.receiverId)) await loadConnection(true);
      }
    }
  }
  async function loadRequests() {
    const current = ++listRevision, selected = direction;
    listController?.abort(); listController = new AbortController(); const signal = listController.signal;
    byId('requests').setAttribute('aria-busy', 'true'); byId('requests').replaceChildren();
    byId('requests-message').textContent = 'Loading your requests…'; byId('refresh').disabled = true;
    try {
      const rows = await api(`/api/match-requests?direction=${selected}`, {signal});
      if (!Array.isArray(rows) || !rows.every(row => positiveId(row.id) && positiveId(row.skillId) && uuid(row.senderId) && uuid(row.receiverId))) throw new Error('Your requests could not be read. Refresh to try again.');
      const [people, catalog] = rows.length ? await Promise.all([partnersFor(rows, selected, signal), skillCatalog().catch(() => new Map())]) : [new Map(), new Map()];
      if (current !== listRevision) return false;
      let incomplete = false;
      if (!rows.length) byId('requests').append(element('p', `No ${selected} requests yet.`, 'empty'));
      for (const row of rows) {
        const target = selected === 'incoming' ? row.senderId : row.receiverId, profile = people.get(target), skill = catalog.get(row.skillId);
        if (!profile || !skill) incomplete = true;
        const card = element('article', '', 'request'); card.dataset.requestId = row.id;
        const info = element('div', '', 'request-info'), heading = element('div', '', 'request-person');
        const avatar = element('span', profile ? initials(nameOf(profile)) : '?', 'request-avatar'); avatar.setAttribute('aria-hidden', 'true');
        const identity = element('div'); identity.append(element('h3', `${selected === 'incoming' ? 'From' : 'To'} ${profile ? nameOf(profile) : 'a Sparkz member'}`), element('p', profile?.schoolName || 'Profile unavailable', 'request-school'));
        heading.append(avatar, identity); info.append(heading, element('p', skill || 'Skill details unavailable', 'request-skill'));
        const date = new Date(row.createdAt); info.append(element('p', Number.isNaN(date.valueOf()) ? 'Date unavailable' : date.toLocaleString(), 'request-date'));
        card.append(info);
        const side = element('div', '', 'request-side'); side.append(element('span', row.status, 'status'));
        if (row.status === 'Pending') {
          const actions = element('div', '', 'actions');
          for (const status of selected === 'incoming' ? ['Accepted', 'Declined'] : ['Canceled']) {
            const button = element('button', {Accepted: 'Accept', Declined: 'Decline', Canceled: 'Cancel'}[status]); button.type = 'button';
            if (status === 'Accepted' && (!profile || !skill || ![...profile.canLearn, ...profile.canTeach].some(s => Number(s.skillId) === Number(row.skillId)))) button.disabled = true;
            button.addEventListener('click', () => changeStatus(row, status, actions, selected)); actions.append(button);
          }
          side.append(actions);
        } else if (row.status === 'Accepted' && document.body.hasAttribute('data-session-planner')) {
          const link = element('a', 'Schedule a session →', 'schedule-session'); link.href = `/sessions.html?${new URLSearchParams({matchRequestId: row.id})}`; side.append(link);
        }
        card.append(side); byId('requests').append(card);
      }
      byId('requests-message').textContent = incomplete ? 'Some connection details are unavailable. Refresh to try again.' : '';
      return true;
    } catch (error) { if (current === listRevision) byId('requests-message').textContent = error.message; return false; }
    finally { if (current === listRevision) { byId('requests').setAttribute('aria-busy', 'false'); byId('refresh').disabled = false; } }
  }
  function chooseDirection(next) {
    direction = next; for (const tab of ['incoming', 'outgoing']) byId(tab).setAttribute('aria-pressed', String(tab === next));
    return loadRequests();
  }
  for (const tab of ['incoming', 'outgoing']) byId(tab).addEventListener('click', () => chooseDirection(tab));
  byId('refresh').addEventListener('click', loadRequests);
  byId('connection-retry').addEventListener('click', () => loadConnection(true));
  byId('skill').addEventListener('change', () => updateComposer(''));
  byId('send-form').addEventListener('submit', async event => {
    event.preventDefault();
    const skillId = Number(byId('skill').value);
    if (sending || loadingPartner || needsCheck || !partner || pairStatus || !skills.has(skillId)) return;
    sending = true; updateComposer(); const epoch = authRevision;
    try {
      await api('/api/match-requests', {method: 'POST', body: JSON.stringify({receiverId: partner.userId, skillId})});
      if (epoch !== authRevision) return;
      pairStatus = 'pending'; message('Request sent.');
      if (!(await chooseDirection('outgoing'))) message('Request sent. Refresh the list to view it.');
    } catch (error) {
      if (epoch !== authRevision) return;
      message(error.message, true);
      if (error.uncertain) { needsCheck = true; byId('connection-retry').hidden = false; byId('connection-retry').textContent = 'Check request status'; }
      else if ([404, 409, 422].includes(error.status)) { await loadConnection(true); await loadRequests(); }
    } finally { if (epoch === authRevision) { sending = false; updateComposer(); } }
  });
  window.addEventListener('sparkz:auth-changed', () => {
    if (internalAuthEvent) return;
    authRevision++; pendingActions.clear(); sessionPromise = catalogPromise = undefined; sending = false; needsCheck = false;
    message(''); loadConnection(); loadRequests();
  });
  loadConnection(); loadRequests();
})();
