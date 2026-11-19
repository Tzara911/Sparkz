/* #5 supplies window.sparkzAuth.getAccessToken(), as on matches.js. */
(() => {
  const byId = id => document.getElementById(id);
  const weekdays = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  let offset = 0, revision = 0;
  const limit = 12;
  let identityAnnounced = false;
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
    if (!identityAnnounced) {
      identityAnnounced = true;
      window.dispatchEvent(new Event('sparkz:auth-changed'));
    }
    return response.json();
  }
  function render(person) {
    const card = element('article', '', 'person');
    const name = person.username || person.firstName || 'Sparkz member';
    const heading = element('div', '', 'person-heading');
    const avatar = element('span', Array.from(name.trim())[0]?.toLocaleUpperCase() || '?', 'person-avatar');
    avatar.setAttribute('aria-hidden', 'true');
    const identity = element('div', '', 'person-identity');
    identity.append(element('h2', name), element('p', person.schoolName, 'person-school'));
    if (person.reciprocal) identity.append(element('span', 'A two-way spark ✦', 'badge'));
    heading.append(avatar, identity);
    card.append(heading);
    card.append(element('p', person.bio || 'No introduction added yet.', 'person-bio'));
    for (const type of ['teach', 'learn']) {
      const row = element('div', '', 'skill-row');
      const tags = element('div', '', 'skill-tags');
      const skills = person.skills.filter(s => s.skillType === type);
      for (const skill of skills) tags.append(element('span', skill.skillName, 'skill-tag'));
      if (!skills.length) tags.append(element('span', 'Not added yet', 'no-skills'));
      row.append(element('h3', type === 'teach' ? 'Can teach:' : 'Wants to learn:'), tags);
      card.append(row);
    }
    const availability = element('div', '', 'availability-row');
    const days = element('div', '', 'day-tags');
    for (const day of person.availableDays) {
      const shared = person.commonDays.includes(day);
      const chip = element('span', weekdays[day].slice(0, 3), `day-tag${shared ? ' is-shared' : ''}`);
      chip.title = shared ? `You are both available on ${weekdays[day]}` : weekdays[day];
      chip.setAttribute('aria-label', chip.title);
      days.append(chip);
    }
    if (!person.availableDays.length) days.append(element('span', 'No days shared yet', 'no-skills'));
    availability.append(element('h3', 'Available days:'), days);
    card.append(availability);
    const reason = person.reciprocal ? 'You can teach each other.' : person.reasons[0];
    if (reason) {
      const note = element('p', '', 'match-note');
      const spark = element('span', '✦', 'spark');
      spark.setAttribute('aria-hidden', 'true');
      note.append(spark, element('span', reason));
      card.append(note);
    }
    const actions = element('div', '', 'person-actions');
    if (person.matchStatus) actions.append(element('p', person.matchStatus === 'accepted' ? 'You are already matched.' : 'A match request is pending.', 'match-state'));
    else {
      const compatible = new Map([...person.canLearn, ...person.canTeach].map(s => [s.skillId, s]));
      if (compatible.size) {
        const link = element('a', `Connect with ${person.firstName || name}`, 'connect-link');
        const arrow = element('span', '', 'link-arrow');
        arrow.innerHTML = '<svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 15 15 5M5 5h10v10"/></svg>';
        arrow.setAttribute('aria-hidden', 'true');
        link.append(arrow);
        const setSkill = skill => {
          link.href = `/matches.html?${new URLSearchParams({receiverId: person.userId, skillId: skill.skillId})}`;
          link.setAttribute('aria-label', `Connect with ${person.firstName || name} over ${skill.skillName}`);
        };
        const initialSkill = compatible.get(Number(byId('skill').value)) || compatible.values().next().value;
        setSkill(initialSkill);
        if (compatible.size > 1) {
          const label = element('label', 'Connect over', 'skill-choice');
          const select = element('select', '');
          for (const skill of compatible.values()) {
            const option = element('option', skill.skillName);
            option.value = String(skill.skillId);
            select.append(option);
          }
          select.value = String(initialSkill.skillId);
          select.addEventListener('change', () => setSkill([...compatible.values()].find(s => String(s.skillId) === select.value)));
          label.append(select);
          actions.append(label);
        }
        actions.append(link);
      } else actions.append(element('p', 'No shared skill match yet.', 'match-state'));
    }
    card.append(actions);
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
      byId('message').textContent = `${data.total} ${data.total === 1 ? 'person' : 'people'} to meet`;
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
