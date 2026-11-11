/* #5 supplies window.sparkzAuth.getAccessToken(), as on matches.js. */
(() => {
  const byId = id => document.getElementById(id);
  const weekdays = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  let offset = 0, revision = 0;
  const limit = 12;
  function element(tag, text, className) {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  async function api(path) {
    const token = await window.sparkzAuth?.getAccessToken();
    const response = await fetch(path, {headers: {Accept: 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {})}});
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (_) { /* Basic auth can return an empty body. */ }
      throw new Error(problem.detail || (response.status === 401 ? 'Please sign in to discover people.' : `Could not load people (${response.status}).`));
    }
    return response.json();
  }
  function render(person) {
    const card = element('article', '', 'person');
    if (person.reciprocal) card.append(element('span', 'You can teach each other', 'badge'));
    card.append(element('h2', person.username || person.firstName || 'Sparkz member'));
    card.append(element('p', person.schoolName));
    if (person.bio) card.append(element('p', person.bio));
    for (const type of ['teach', 'learn']) {
      const skills = person.skills.filter(s => s.skillType === type).map(s => s.skillName);
      if (skills.length) card.append(element('h3', type === 'teach' ? 'Can teach' : 'Wants to learn'), element('p', skills.join(', ')));
    }
    card.append(element('h3', 'General availability'), element('p', person.availableDays.length ? person.availableDays.map(d => weekdays[d]).join(', ') : 'No days shared yet'));
    if (person.reasons.length) {
      const list = element('ul', '');
      person.reasons.forEach(reason => list.append(element('li', reason)));
      card.append(list);
    }
    if (person.matchStatus) card.append(element('p', person.matchStatus === 'accepted' ? 'You are already matched.' : 'A match request is pending.'));
    else {
      const compatible = new Map([...person.canLearn, ...person.canTeach].map(s => [s.skillId, s]));
      for (const skill of compatible.values()) {
        const link = element('a', `Connect over ${skill.skillName} →`);
        link.href = `/matches.html?${new URLSearchParams({receiverId: person.userId, skillId: skill.skillId})}`;
        card.append(link);
      }
    }
    return card;
  }
  async function load() {
    const current = ++revision;
    byId('people').setAttribute('aria-busy', 'true');
    byId('message').textContent = 'Finding people…';
    byId('message').className = '';
    byId('previous').disabled = byId('next').disabled = true;
    const query = new URLSearchParams({offset, limit, recommended: byId('recommended').value});
    for (const [id, name] of [['skill', 'skillId'], ['skill-type', 'skillType'], ['day', 'day']]) if (byId(id).value) query.set(name, byId(id).value);
    try {
      const data = await api(`/api/discovery?${query}`);
      if (current !== revision) return;
      byId('people').replaceChildren(...data.profiles.map(render));
      if (!data.profiles.length) byId('people').append(element('p', 'No people found. Try another skill or available day.', 'empty'));
      byId('message').textContent = `${data.total} ${data.total === 1 ? 'person' : 'people'} found`;
      byId('page-count').textContent = data.total ? `${offset + 1}–${offset + data.profiles.length} of ${data.total}` : '0 results';
      byId('previous').disabled = offset === 0;
      byId('next').disabled = offset + limit >= data.total;
    } catch (e) {
      if (current === revision) {
        byId('people').replaceChildren();
        byId('page-count').textContent = '';
        byId('message').textContent = e.message;
        byId('message').className = 'error';
      }
    } finally { if (current === revision) byId('people').setAttribute('aria-busy', 'false'); }
  }
  byId('filters').addEventListener('submit', e => { e.preventDefault(); offset = 0; load(); });
  byId('previous').addEventListener('click', () => { offset = Math.max(0, offset - limit); load(); });
  byId('next').addEventListener('click', () => { offset += limit; load(); });
  (async () => {
    try {
      const skills = await api('/api/skills');
      for (const skill of skills) {
        const option = element('option', skill.skillName);
        option.value = skill.skillId;
        byId('skill').append(option);
      }
    } catch (e) { byId('message').textContent = e.message; }
    await load();
  })();
})();
