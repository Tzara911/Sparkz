/* #5 supplies window.sparkzAuth.getAccessToken(), as on matches.js/discovery.js. */
(() => {
  const byId = id => document.getElementById(id);
  const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  let session;

  async function api(path, options = {}) {
    const headers = { Accept: 'application/json', ...options.headers };
    const token = await window.sparkzAuth?.getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    else {
      if (!session) {
        const response = await fetch('/api/match-session');
        if (!response.ok) throw new Error('Please sign in to manage your profile.');
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
    return response.status === 204 ? null : response.json();
  }

  function message(id, text, error = false) {
    const node = byId(id);
    node.textContent = text;
    node.className = error ? 'error' : '';
  }

  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text) node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  function fillForm(profile) {
    for (const field of ['username', 'email', 'firstName', 'lastName', 'schoolName', 'gradeYear',
      'address', 'phone', 'dob', 'timeZone', 'bio', 'avatarUrl']) {
      byId(field).value = profile[field] || '';
    }
    byId('toggle-status').textContent = profile.accountStatus === 'active' ? 'Deactivate account' : 'Reactivate account';
  }

  async function loadProfile() {
    try {
      const profile = await api('/api/profiles/me');
      fillForm(profile);
    } catch (e) { message('profile-message', e.message, true); }
  }

  byId('profile-form').addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.target.querySelector('button');
    button.disabled = true;
    try {
      const body = {};
      for (const field of ['username', 'email', 'firstName', 'lastName', 'schoolName', 'gradeYear',
        'address', 'phone', 'dob', 'timeZone', 'bio', 'avatarUrl']) {
        body[field] = byId(field).value || null;
      }
      const profile = await api('/api/profiles/me', { method: 'PUT', body: JSON.stringify(body) });
      fillForm(profile);
      message('profile-message', 'Profile saved.');
    } catch (e) { message('profile-message', e.message, true); }
    finally { button.disabled = false; }
  });

  byId('toggle-status').addEventListener('click', async () => {
    const button = byId('toggle-status');
    const activating = button.textContent === 'Reactivate account';
    button.disabled = true;
    try {
      const profile = await api(`/api/profiles/me/${activating ? 'activate' : 'deactivate'}`, { method: 'PUT' });
      fillForm(profile);
      message('profile-message', activating ? 'Account reactivated.' : 'Account deactivated.');
    } catch (e) { message('profile-message', e.message, true); }
    finally { button.disabled = false; }
  });

  async function loadSkills() {
    try {
      const [catalog, ownSkills] = await Promise.all([api('/api/skills'), api('/api/profiles/me/skills')]);
      for (const selectId of ['teach-skill-select', 'learn-skill-select']) {
        byId(selectId).replaceChildren(...catalog.map(skill => {
          const option = element('option', skill.skillName);
          option.value = skill.skillId;
          return option;
        }));
      }
      for (const [type, listId] of [['teach', 'teach-list'], ['learn', 'learn-list']]) {
        const rows = ownSkills.filter(skill => skill.skillType === type);
        const list = byId(listId);
        list.replaceChildren();
        if (!rows.length) { list.append(element('li', `No ${type === 'teach' ? 'teaching' : 'learning'} skills yet.`, 'empty')); continue; }
        for (const skill of rows) {
          const item = element('li');
          item.append(element('span', skill.skillName));
          const remove = element('button', 'Remove');
          remove.type = 'button';
          remove.addEventListener('click', async () => {
            remove.disabled = true;
            try {
              await api(`/api/profiles/me/skills/${skill.skillId}/${skill.skillType}`, { method: 'DELETE' });
              await loadSkills();
            } catch (e) { message('skills-message', e.message, true); remove.disabled = false; }
          });
          item.append(remove);
          list.append(item);
        }
      }
    } catch (e) { message('skills-message', e.message, true); }
  }

  for (const [type, formId, selectId] of [['teach', 'add-teach-form', 'teach-skill-select'], ['learn', 'add-learn-form', 'learn-skill-select']]) {
    byId(formId).addEventListener('submit', async event => {
      event.preventDefault();
      const button = event.target.querySelector('button');
      button.disabled = true;
      try {
        const skillId = byId(selectId).value;
        await api(`/api/profiles/me/skills/${skillId}/${type}`, { method: 'PUT' });
        message('skills-message', 'Skill added.');
        await loadSkills();
      } catch (e) { message('skills-message', e.message, true); }
      finally { button.disabled = false; }
    });
  }

  async function loadAvailability() {
    try {
      const availability = await api('/api/profiles/me/availability');
      renderAvailability(availability);
    } catch (e) { message('availability-message', e.message, true); }
  }

  function renderAvailability(availability) {
    const container = byId('availability-days');
    container.replaceChildren();
    weekdays.forEach((name, index) => {
      const day = index + 1;
      const available = availability.days.includes(day);
      const row = element('div', '', 'availability-day');

      const toggle = element('button', available ? `${name} ✓` : name);
      toggle.type = 'button';
      toggle.setAttribute('aria-pressed', String(available));
      toggle.addEventListener('click', async () => {
        const days = available
          ? availability.days.filter(d => d !== day)
          : [...availability.days, day];
        toggle.disabled = true;
        try {
          const updated = await api('/api/profiles/me/availability/days', { method: 'PUT', body: JSON.stringify({ days }) });
          message('availability-message', 'Availability updated.');
          renderAvailability(updated);
        } catch (e) { message('availability-message', e.message, true); toggle.disabled = false; }
      });
      row.append(toggle);

      if (available) {
        const daySlots = availability.slots.filter(slot => slot.dayOfWeek === day);
        const slotList = element('ul', '', 'slot-list');
        if (!daySlots.length) slotList.append(element('li', 'No time blocks added yet.', 'empty'));
        for (const slot of daySlots) {
          const item = element('li');
          item.append(element('span', `${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`));
          const remove = element('button', 'Remove');
          remove.type = 'button';
          remove.addEventListener('click', async () => {
            remove.disabled = true;
            try {
              await api(`/api/profiles/me/availability/slots/${slot.slotId}`, { method: 'DELETE' });
              await loadAvailability();
            } catch (e) { message('availability-message', e.message, true); remove.disabled = false; }
          });
          item.append(remove);
          slotList.append(item);
        }
        row.append(slotList);

        const form = element('form', '', 'slot-form');
        const startInput = element('input');
        startInput.type = 'time';
        startInput.required = true;
        const endInput = element('input');
        endInput.type = 'time';
        endInput.required = true;
        const addButton = element('button', 'Add time block');
        addButton.type = 'submit';
        form.append(startInput, endInput, addButton);
        form.addEventListener('submit', async event => {
          event.preventDefault();
          addButton.disabled = true;
          try {
            await api('/api/profiles/me/availability/slots', {
              method: 'POST',
              body: JSON.stringify({ dayOfWeek: day, startTime: startInput.value, endTime: endInput.value })
            });
            message('availability-message', 'Time block added.');
            await loadAvailability();
          } catch (e) { message('availability-message', e.message, true); }
          finally { addButton.disabled = false; }
        });
        row.append(form);
      }

      container.append(row);
    });
  }

  loadProfile();
  loadSkills();
  loadAvailability();
})();
