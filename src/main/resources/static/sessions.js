/* #5 supplies sparkzAuth.getAccessToken(); the isolated local demo uses Basic + CSRF. */
(() => {
  const byId = id => document.getElementById(id);
  const limit = 6;
  const profiles = new Map();
  let me, skills = new Map(), matches = [], localSession;
  let direction = 'all', offset = 0, revision = 0, initializing = false, busy = false;
  class ApiError extends Error {
    constructor(message, status) { super(message); this.status = status; }
  }
  const message = (text, error = false) => {
    byId('session-message').textContent = text;
    byId('session-message').classList.toggle('error', error);
  };
  const element = (tag, text, className) => {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  async function api(path, options = {}) {
    const headers = {Accept: 'application/json'};
    const token = await window.sparkzAuth?.getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    else {
      if (!localSession) localSession = fetch('/api/match-session').then(async response => {
        if (!response.ok) throw new ApiError('Please sign in to manage learning sessions.', response.status);
        return response.json();
      }).catch(error => { localSession = undefined; throw error; });
      const session = await localSession;
      if (options.method) headers[session.csrfHeader] = session.csrfToken;
    }
    if (options.body) headers['Content-Type'] = 'application/json';
    const response = await fetch(path, {...options, headers});
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (_) { /* Authentication can return an empty response. */ }
      if (response.status === 401) localSession = undefined;
      throw new ApiError(problem.detail || (response.status === 401 ? 'Please sign in to manage learning sessions.' : `Could not complete your request (${response.status}). Please try again.`), response.status);
    }
    return response.json();
  }
  const partnerId = match => match.senderId === me.userId ? match.receiverId : match.senderId;
  const name = profile => profile.username || profile.firstName || 'Learning partner';
  const skillName = id => skills.get(String(id)) || 'Skill exchange';
  function partner(id) {
    if (!profiles.has(id)) profiles.set(id, api(`/api/discovery/${encodeURIComponent(id)}`)
      .then(profile => ({...profile, available: true}))
      .catch(error => ({userId: id, available: false, error})));
    return profiles.get(id);
  }
  function roles(match, profile) {
    const has = list => list?.some(skill => String(skill.skillId) === String(match.skillId));
    const result = [];
    if (has(profile.canTeach)) result.push({id: me.userId, label: `I'm teaching ${skillName(match.skillId)}`});
    if (has(profile.canLearn)) result.push({id: partnerId(match), label: `I'm learning ${skillName(match.skillId)}`});
    return result;
  }
  function setBusy(value) {
    busy = value;
    byId('session-fields').disabled = value || !matches.length;
    byId('propose-session').disabled = value || !byId('session-match').value;
    document.querySelectorAll('.session-actions button').forEach(button => button.disabled = value);
  }
  function selectTeacher() {
    const match = matches.find(row => String(row.id) === byId('session-match').value);
    const select = byId('session-teacher');
    select.replaceChildren();
    for (const role of match?.roles || []) {
      const option = element('option', role.label);
      option.value = role.id;
      select.append(option);
    }
    if (!select.options.length) select.append(element('option', 'Choose a match first'));
    select.disabled = !match;
    byId('propose-session').disabled = busy || !match;
  }
  function localInput(date) {
    const pad = value => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  function localDate(value) {
    const date = new Date(value);
    if (!value || !Number.isFinite(date.getTime()) || localInput(date) !== value)
      throw new Error('Choose a valid local date and time. Times skipped by daylight saving are unavailable.');
    return date;
  }
  const when = value => new Intl.DateTimeFormat(undefined, {
    year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short'
  }).format(new Date(value));
  function timeHints() {
    byId('session-end').min = byId('session-start').value;
    try {
      const start = localDate(byId('session-start').value);
      const end = localDate(byId('session-end').value);
      byId('session-time-preview').textContent = `${when(start)} → ${when(end)}`;
    } catch (_) { byId('session-time-preview').textContent = ''; }
  }
  function modeFields() {
    const online = byId('session-mode').value === 'online';
    byId('meeting-url-field').hidden = !online;
    byId('location-field').hidden = online;
    byId('session-url').disabled = !online;
    byId('session-url').required = online;
    byId('session-location').disabled = online;
    byId('session-location').required = !online;
  }
  function setDirection(value) {
    direction = value;
    offset = 0;
    for (const tab of ['all', 'incoming', 'outgoing']) byId(`sessions-${tab}`).setAttribute('aria-pressed', String(tab === value));
  }
  async function confirmChange(next, title, start) {
    const dialog = byId('session-confirm');
    if (dialog.open) return false;
    const action = next === 'canceled' ? 'Cancel' : 'Decline';
    byId('session-confirm-title').textContent = `${action} this session?`;
    byId('session-confirm-detail').textContent = `${title} · ${when(start)}. This invitation cannot be reopened afterward.`;
    byId('session-confirm-action').textContent = `${action} session`;
    dialog.returnValue = 'keep';
    const result = new Promise(resolve => dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), {once: true}));
    dialog.showModal();
    return result;
  }
  async function change(session, next, title) {
    if (busy) return;
    if (next !== 'accepted' && !(await confirmChange(next, title, session.scheduledStart))) return;
    setBusy(true);
    try {
      await api(`/api/learning-sessions/${session.id}`, {method: 'PATCH', body: JSON.stringify({status: next})});
      message(`Session ${next}.`);
      await loadSessions();
    } catch (error) {
      message(error.message, true);
      if (error.status === 409 || error.status === 422) await loadSessions();
    } finally { setBusy(false); }
  }
  async function card(session) {
    const other = session.teacherId === me.userId ? session.learnerId : session.teacherId;
    const profile = await partner(other);
    const match = [...acceptedMatches.values()].find(row => row.id === session.matchRequestId);
    const title = `${match ? skillName(match.skillId) : 'Skill exchange'} with ${name(profile)}`;
    const article = element('article', '', 'session-card');
    article.dataset.sessionId = session.id;
    const heading = element('div', '', 'session-card-heading');
    const identity = element('div', '');
    identity.append(element('h3', title), element('p', `${session.teacherId === me.userId ? "You're teaching" : "You're learning"} · ${session.proposedBy === me.userId ? 'Proposed by you' : 'Proposed by your partner'}`, 'session-card-role'));
    const status = session.status[0].toUpperCase()+session.status.slice(1);
    heading.append(identity, element('span', status, `session-status ${session.status}`));
    article.append(heading);
    const details = element('dl', '', 'session-details');
    const detail = (label, value) => {
      const row = element('div', '');
      row.append(element('dt', label), element('dd', value));
      details.append(row);
      return row.lastChild;
    };
    detail('Starts', when(session.scheduledStart));
    detail('Ends', when(session.scheduledEnd));
    const place = detail('Meet', session.mode === 'online' ? 'Online' : session.location || 'In person');
    if (session.mode === 'online') {
      try {
        const url = new URL(session.meetingUrl);
        if (['https:', 'http:'].includes(url.protocol) && !url.username && !url.password) {
          const link = element('a', 'Join online session →');
          link.href = url.href;
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
          place.replaceChildren(link);
        }
      } catch (_) { /* Keep the mode label if an old row has no valid meeting URL. */ }
    }
    article.append(details);
    if (session.message) article.append(element('p', session.message, 'session-note'));
    if (new Date(session.scheduledStart).getTime() > Date.now() && ['pending', 'accepted'].includes(session.status)) {
      if (session.status === 'pending') article.append(element('p', session.proposedBy === me.userId ? 'Awaiting your partner’s reply.' : 'Your partner is waiting for your reply.', 'session-reply-hint'));
      const actions = element('div', '', 'session-actions');
      const states = session.status === 'pending' && session.proposedBy !== me.userId ? ['accepted', 'declined', 'canceled'] : ['canceled'];
      for (const state of states) {
        const label = {accepted: 'Accept', declined: 'Decline', canceled: 'Cancel'}[state];
        const button = element('button', label, state === 'accepted' ? 'session-primary' : '');
        button.type = 'button';
        button.disabled = busy;
        button.setAttribute('aria-label', `${label} session with ${name(profile)}`);
        button.addEventListener('click', () => change(session, state, title));
        actions.append(button);
      }
      article.append(actions);
    } else if (['pending', 'accepted'].includes(session.status)) article.append(element('p', 'This session’s start time has passed.', 'session-reply-hint'));
    return article;
  }
  const acceptedMatches = new Map();
  async function loadSessions() {
    const current = ++revision;
    byId('session-list').setAttribute('aria-busy', 'true');
    byId('sessions-previous').disabled = byId('sessions-next').disabled = true;
    try {
      const data = await api(`/api/learning-sessions?${new URLSearchParams({direction, offset, limit})}`);
      const cards = await Promise.all(data.sessions.map(card));
      if (current !== revision) return;
      if (!cards.length && offset > 0 && data.total > 0) {
        offset = Math.floor((data.total - 1) / limit) * limit;
        return loadSessions();
      }
      byId('session-list').replaceChildren(...cards);
      if (!cards.length) byId('session-list').append(element('p', direction === 'all' ? 'No sessions yet. Plan a session with an accepted match to get started.' : `No ${direction} sessions yet.`, 'session-empty'));
      byId('session-count').textContent = `${data.total} ${data.total === 1 ? 'session' : 'sessions'}`;
      byId('session-page-count').textContent = data.total ? `${offset+1}–${offset+cards.length} of ${data.total}` : '0 sessions';
      byId('sessions-previous').disabled = offset === 0;
      byId('sessions-next').disabled = offset + limit >= data.total;
    } catch (error) {
      if (current === revision) {
        byId('session-list').replaceChildren(element('p', 'Your sessions could not be loaded. Use Refresh to try again.', 'session-empty'));
        byId('session-count').textContent = '';
        byId('session-page-count').textContent = '';
        message(error.message, true);
      }
    } finally { if (current === revision) byId('session-list').setAttribute('aria-busy', 'false'); }
  }
  async function initialize() {
    if (initializing || busy) return;
    initializing = true;
    profiles.clear();
    byId('session-fields').disabled = true;
    byId('planner-state').textContent = 'Loading your accepted matches…';
    byId('sessions-refresh').disabled = true;
    const previousMatch = byId('session-match').value;
    try {
      const [profile, incoming, outgoing, catalog] = await Promise.all([
        api('/api/profiles/me'), api('/api/match-requests?direction=incoming'),
        api('/api/match-requests?direction=outgoing'), api('/api/skills')
      ]);
      me = profile;
      window.dispatchEvent(new Event('sparkz:auth-changed'));
      skills = new Map(catalog.map(skill => [String(skill.skillId), skill.skillName]));
      acceptedMatches.clear();
      for (const match of [...incoming, ...outgoing]) if (match.status === 'Accepted') acceptedMatches.set(match.id, match);
      const rows = await Promise.all([...acceptedMatches.values()].map(async match => {
        const profile = await partner(partnerId(match));
        return {...match, profile, roles: roles(match, profile)};
      }));
      matches = rows.filter(match => match.profile.available && match.roles.length);
      const select = byId('session-match');
      select.replaceChildren(element('option', 'Choose an accepted match'));
      select.firstChild.value = '';
      for (const match of matches) {
        const option = element('option', `${name(match.profile)} · ${skillName(match.skillId)}`);
        option.value = match.id;
        select.append(option);
      }
      const requested = previousMatch || new URLSearchParams(location.search).get('matchRequestId');
      if (matches.some(match => String(match.id) === requested)) select.value = requested;
      else if (matches.length === 1) select.value = matches[0].id;
      const failedDetails = rows.some(match => match.profile.error && match.profile.error.status !== 404);
      byId('planner-state').textContent = matches.length ? '' : failedDetails ? 'Partner details could not be loaded. Use Refresh to try again.' : 'No accepted matches are ready for scheduling. Check your match requests or refresh after your partner accepts.';
      if (requested && !matches.some(match => String(match.id) === requested)) message('That match is no longer available for new sessions. Choose another accepted match.', true);
      else if (failedDetails) message('Some partner details could not be loaded. Use Refresh to try again.', true);
      else message('');
      byId('session-fields').disabled = !matches.length;
      selectTeacher();
      await loadSessions();
    } catch (error) {
      me = undefined;
      matches = [];
      byId('planner-state').textContent = 'Sign in and complete your profile to plan a session.';
      byId('session-list').replaceChildren(element('p', 'Sign in to view your learning sessions, then select Refresh.', 'session-empty'));
      byId('session-list').setAttribute('aria-busy', 'false');
      message(error.message, true);
    } finally {
      initializing = false;
      byId('sessions-refresh').disabled = false;
    }
  }
  byId('session-match').addEventListener('change', selectTeacher);
  byId('session-mode').addEventListener('change', modeFields);
  byId('session-start').addEventListener('change', () => {
    try {
      const start = localDate(byId('session-start').value);
      const end = new Date(byId('session-end').value);
      if (!Number.isFinite(end.getTime()) || end <= start) byId('session-end').value = localInput(new Date(start.getTime()+3600000));
    } catch (_) { /* Report an invalid time on submission. */ }
    timeHints();
  });
  byId('session-end').addEventListener('change', timeHints);
  byId('session-start').min = localInput(new Date());
  byId('session-time-zone').textContent = `Times are in your device’s time zone: ${Intl.DateTimeFormat().resolvedOptions().timeZone}.`;
  byId('session-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || initializing) return;
    try {
      const start = localDate(byId('session-start').value), end = localDate(byId('session-end').value);
      if (start.getTime() <= Date.now()) throw new Error('Choose a start time in the future.');
      if (end <= start) throw new Error('The end time must be after the start time.');
      const matchRequestId = Number(byId('session-match').value);
      if (!Number.isSafeInteger(matchRequestId) || !matches.some(match => match.id === matchRequestId)) throw new Error('Choose an accepted match.');
      const body = {matchRequestId, teacherId: byId('session-teacher').value,
        scheduledStart: start.toISOString(), scheduledEnd: end.toISOString(), mode: byId('session-mode').value,
        location: byId('session-location').value.trim(), meetingUrl: byId('session-url').value.trim(), message: byId('session-note').value.trim()};
      setBusy(true);
      await api('/api/learning-sessions', {method: 'POST', body: JSON.stringify(body)});
      event.target.reset();
      byId('session-match').value = String(matchRequestId);
      selectTeacher();
      modeFields();
      timeHints();
      setDirection('outgoing');
      message('Invitation sent. You can find it under Outgoing.');
      await loadSessions();
    } catch (error) { message(error.message, true); }
    finally { setBusy(false); }
  });
  for (const tab of ['all', 'incoming', 'outgoing']) byId(`sessions-${tab}`).addEventListener('click', () => {
    setDirection(tab);
    if (me) loadSessions();
  });
  byId('sessions-previous').addEventListener('click', () => { offset = Math.max(0, offset-limit); loadSessions(); });
  byId('sessions-next').addEventListener('click', () => { offset += limit; loadSessions(); });
  byId('sessions-refresh').addEventListener('click', initialize);
  modeFields();
  initialize();
})();
