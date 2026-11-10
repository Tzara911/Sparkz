(() => {
  const api = window.sparkzApi;
  const byId = id => document.getElementById(id);
  const weekdays = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  let mode = 'recommended', page = 0, hasNext = false, revision = 0;
  let filters = new URLSearchParams();
  function element(tag, text, className) {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  function message(text, error = false) {
    byId('message').textContent = text;
    byId('message').className = error ? 'error' : '';
  }
  function names(skills) { return skills.map(s => s.name).join(', '); }
  function days(values) { return values.map(d => weekdays[d]).join(', '); }
  function skillSection(card, heading, skills) {
    card.append(element('h3', heading));
    const tags = element('div', '', 'tags');
    if (!skills.length) tags.append(element('p', 'No skills shared yet.'));
    skills.forEach(s => tags.append(element('span', s.name, 'tag')));
    card.append(tags);
  }
  function profileCard(profile) {
    const card = element('article', '', 'profile');
    if (profile.reciprocal) card.append(element('span', 'Mutual skill exchange', 'badge'));
    card.append(element('h2', profile.username || 'Sparkz member'), element('p', profile.schoolName, 'school'));
    if (profile.bio) card.append(element('p', profile.bio, 'bio'));
    skillSection(card, 'Can teach', profile.teachingSkills);
    skillSection(card, 'Wants to learn', profile.learningSkills);
    card.append(element('h3', 'Available days'), element('p', days(profile.availableDays) || 'No days shared yet.'));
    const reason = element('div', '', 'reason');
    reason.append(element('strong', 'Your connection'));
    if (profile.learnFromThem.length) reason.append(element('p', `Learn from them: ${names(profile.learnFromThem)}.`));
    if (profile.teachThem.length) reason.append(element('p', `You can teach them: ${names(profile.teachThem)}.`));
    reason.append(element('p', profile.commonDays.length ? `Days in common: ${days(profile.commonDays)}.` : 'No shared available days yet.'));
    card.append(reason);
    const compatible = [...new Map([...profile.learnFromThem, ...profile.teachThem].map(s => [s.id, s])).values()];
    if (!compatible.length) {
      card.append(element('p', 'No complementary skills yet. Add skills to your profile to find more connections.'));
      return card;
    }
    const form = document.createElement('form');
    const label = element('label', 'Choose a skill to connect over');
    const select = document.createElement('select');
    compatible.forEach(skill => select.add(new Option(skill.name, String(skill.id))));
    label.append(select);
    const button = element('button', 'Send request ↗');
    button.type = 'submit';
    form.append(label, button);
    form.addEventListener('submit', async event => {
      event.preventDefault();
      button.disabled = true;
      select.disabled = true;
      const current = revision;
      try {
        // Use #8's authenticated, transactional endpoint; eligibility is checked again there.
        await api('/api/match-requests', {method: 'POST', body: JSON.stringify({receiverId: profile.userId, skillId: select.value})});
        message('Request sent. You can track it in Match requests.');
        await load(true);
      } catch (error) {
        if (current !== revision) return;
        message(error.message, true);
        if (error.status === 409 || error.status === 422 || error.status === 404) await load(true);
      } finally { button.disabled = false; select.disabled = false; }
    });
    card.append(form);
    return card;
  }
  async function load(keepMessage = false) {
    const current = ++revision;
    const query = new URLSearchParams(filters);
    query.set('mode', mode); query.set('page', String(page)); query.set('size', '12');
    byId('profiles').replaceChildren();
    byId('profiles').setAttribute('aria-busy', 'true');
    byId('previous').disabled = byId('next').disabled = true;
    if (!keepMessage) message('Finding people…');
    try {
      const result = await api(`/api/discovery?${query}`);
      if (current !== revision) return;
      if (!result.profiles.length && page > 0) { page--; return load(keepMessage); }
      hasNext = result.hasNext;
      result.profiles.forEach(profile => byId('profiles').append(profileCard(profile)));
      if (!result.profiles.length) byId('profiles').append(element('p', 'No people found. Try different filters or browse all profiles.', 'empty'));
      if (!keepMessage) message(`${result.profiles.length} ${result.profiles.length === 1 ? 'person' : 'people'} on this page.`);
      byId('page-label').textContent = `Page ${page + 1}`;
      byId('previous').disabled = page === 0;
      byId('next').disabled = !hasNext;
    } catch (error) { if (current === revision) message(error.message, true); }
    finally { if (current === revision) byId('profiles').setAttribute('aria-busy', 'false'); }
  }
  for (const selected of ['recommended', 'browse']) byId(selected).addEventListener('click', () => {
    mode = selected; page = 0;
    for (const id of ['recommended', 'browse']) byId(id).setAttribute('aria-pressed', String(id === mode));
    byId('view-help').textContent = mode === 'recommended'
      ? 'People with skills that complement yours. Mutual exchanges appear first.'
      : 'Eligible people at your school. People you already have a pending or accepted match with appear in Match requests.';
    load();
  });
  byId('filters').addEventListener('submit', event => {
    event.preventDefault();
    filters = new URLSearchParams();
    for (const key of ['teaches', 'learns', 'day']) if (byId(key).value) filters.set(key, byId(key).value);
    page = 0; load();
  });
  byId('filters').addEventListener('reset', () => { filters = new URLSearchParams(); page = 0; load(); });
  byId('previous').addEventListener('click', () => { if (page > 0) { page--; load(); } });
  byId('next').addEventListener('click', () => { if (hasNext) { page++; load(); } });
  byId('refresh').addEventListener('click', () => load());
  async function start() {
    try {
      const skills = await api('/api/discovery/skills');
      for (const id of ['teaches', 'learns']) skills.forEach(s => byId(id).add(new Option(s.name, String(s.id))));
      await load();
    } catch (error) { message(error.message, true); byId('profiles').setAttribute('aria-busy', 'false'); }
  }
  start();
})();
