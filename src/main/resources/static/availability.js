(() => {
  const byId = id => document.getElementById(id);
  const names = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  const demo = new URLSearchParams(location.search).get('demo') === '1';
  const storageKey = 'sparkz-demo-availability';
  let data = null, session, ready = false, busy = false;
  const message = (id, text, error = false) => { byId(id).textContent = text; byId(id).classList.toggle('is-error', error); };

  function snapshot(value) {
    const day = n => Number.isInteger(n) && n >= 1 && n <= 7;
    const time = s => typeof s === 'string' && /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/.test(s);
    if (!value || !Array.isArray(value.days) || !value.days.every(day) || !Array.isArray(value.slots)
      || !value.slots.every(s => s && Number.isSafeInteger(s.slotId) && s.slotId > 0 && day(s.dayOfWeek)
        && value.days.includes(s.dayOfWeek) && time(s.startTime) && time(s.endTime))
      || new Set(value.slots.map(s => s.slotId)).size !== value.slots.length
      || (value.timeZone != null && typeof value.timeZone !== 'string')) {
      throw new Error('Availability could not be read. Retry loading before making changes.');
    }
    return {days: [...new Set(value.days)].sort((a, b) => a - b),
      slots: value.slots.map(s => ({...s})), timeZone: value.timeZone || null};
  }
  function readDemo() {
    try { const saved = localStorage.getItem(storageKey); if (saved) return snapshot(JSON.parse(saved)); }
    catch (_) { /* Invalid demo storage starts from the fictional sample. */ }
    return {days: [1, 3, 5], slots: [
      {slotId: 1, dayOfWeek: 1, startTime: '09:00', endTime: '11:00'},
      {slotId: 2, dayOfWeek: 3, startTime: '13:00', endTime: '15:00'},
      {slotId: 3, dayOfWeek: 5, startTime: '10:00', endTime: '12:00'}
    ], timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone};
  }
  function saveDemo(next) { localStorage.setItem(storageKey, JSON.stringify(next)); return next; }
  async function api(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    const writing = Boolean(options.method);
    try {
      const headers = {Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest', ...options.headers};
      const token = await window.sparkzAuth?.getAccessToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      else {
        if (!session) {
          const response = await fetch('/api/match-session', {headers: {'X-Requested-With': 'XMLHttpRequest'}, signal: controller.signal});
          if (!response.ok) throw Object.assign(new Error('Please sign in before managing availability.'), {status: response.status});
          session = await response.json();
          window.dispatchEvent(new Event('sparkz:auth-changed'));
        }
        if (writing) headers[session.csrfHeader] = session.csrfToken;
      }
      if (options.body) headers['Content-Type'] = 'application/json';
      let response;
      try { response = await fetch(path, {...options, headers, signal: controller.signal, cache: 'no-store'}); }
      catch (_) { throw Object.assign(new Error('The request could not be confirmed. Retry loading before making more changes.'), {uncertain: writing}); }
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) session = undefined;
        let problem = {};
        try { problem = await response.json(); } catch (_) { /* Authentication may return no body. */ }
        throw Object.assign(new Error(problem.detail || `Could not ${writing ? 'save' : 'load'} availability (${response.status}).`), {status: response.status});
      }
      if (response.status === 204) return null;
      try { return await response.json(); }
      catch (_) { throw Object.assign(new Error('The response could not be read. Retry loading before making more changes.'), {uncertain: writing}); }
    } finally { clearTimeout(timeout); }
  }
  const read = () => demo ? Promise.resolve(readDemo()) : api('/api/profiles/me/availability');
  function controls() {
    document.querySelectorAll('#days-form input, #days-form button, #slot-form input, #slot-form select, #slot-form button, .slot-actions button')
      .forEach(control => { control.disabled = !ready || busy; });
    byId('availability-retry').disabled = busy;
    for (const id of ['days-form', 'slot-form', 'slot-list']) byId(id).setAttribute('aria-busy', String(busy));
  }
  function resetSlotForm() {
    byId('slot-form').reset(); byId('slot-id').value = '';
    byId('slot-save').innerHTML = 'Add time slot <span aria-hidden="true">+</span>';
    byId('slot-cancel').hidden = true;
  }
  function apply(value) {
    const next = snapshot(value), selectedDay = byId('slot-day').value;
    data = next;
    document.querySelectorAll('.day-choice input').forEach(input => { input.checked = data.days.includes(Number(input.value)); });
    byId('slot-day').replaceChildren(new Option('Choose a day', ''));
    for (const day of data.days) byId('slot-day').add(new Option(names[day], String(day)));
    if (data.days.includes(Number(selectedDay))) byId('slot-day').value = selectedDay;
    const id = byId('slot-id').value;
    const editing = data.slots.find(s => s.slotId === Number(id));
    if (id && !editing) resetSlotForm();
    else if (editing && !byId('slot-day').value) byId('slot-day').value = String(editing.dayOfWeek);
    byId('availability-time-zone').textContent = data.timeZone
      ? `Times are in your profile's time zone: ${data.timeZone}.`
      : 'Your profile time zone is not set. Confirm your time zone before adding detailed times.';
    renderSlots();
  }
  function formatTime(value) {
    const [hour, minute] = value.split(':').map(Number);
    return new Date(2000, 0, 1, hour, minute).toLocaleTimeString([], {hour: 'numeric', minute: '2-digit'});
  }
  function renderSlots() {
    const list = byId('slot-list'); list.replaceChildren();
    const slots = [...data.slots].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime));
    byId('slot-count').textContent = `${slots.length} saved`;
    if (!slots.length) {
      const p = document.createElement('p'); p.className = 'empty-state';
      p.textContent = 'No time slots yet. Add a window above when you know you are free.'; list.append(p);
    }
    for (const slot of slots) {
      const item = document.createElement('article'); item.className = 'slot-item';
      const details = document.createElement('div'), title = document.createElement('strong'), time = document.createElement('p');
      title.textContent = names[slot.dayOfWeek]; time.textContent = `${formatTime(slot.startTime)} – ${formatTime(slot.endTime)}`;
      details.append(title, time);
      const actions = document.createElement('div'); actions.className = 'slot-actions';
      const edit = document.createElement('button'); edit.type = 'button'; edit.textContent = 'Edit'; edit.addEventListener('click', () => editSlot(slot));
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = 'Remove'; remove.className = 'delete-slot'; remove.addEventListener('click', () => removeSlot(slot));
      actions.append(edit, remove); item.append(details, actions); list.append(item);
    }
  }
  function requireReload(id, text) {
    ready = false; byId('availability-retry').hidden = false;
    message(id, text, true);
    if (id !== 'days-message') message('days-message', 'Reload availability before making more changes.', true);
  }
  async function load() {
    if (busy) return;
    busy = true; ready = false; controls(); byId('availability-retry').hidden = true;
    message('days-message', 'Loading your availability…'); message('slot-message', '');
    try { apply(await read()); ready = true; message('days-message', ''); }
    catch (error) {
      requireReload('days-message', error.message);
      if (!data) byId('slot-list').replaceChildren(Object.assign(document.createElement('p'), {
        className: 'empty-state', textContent: 'Availability could not be loaded. Retry to continue.'}));
    } finally { busy = false; controls(); }
  }
  async function mutate(id, save, success, {returnsSnapshot = demo, resetEdit = false, slotId = null} = {}) {
    if (!ready || busy) return;
    busy = true; controls(); message(id, 'Saving…'); let saved = false;
    try {
      const response = await save(); saved = true;
      if (resetEdit) resetSlotForm();
      apply(returnsSnapshot ? response : await read()); ready = true;
      byId('availability-retry').hidden = true; message(id, success);
    } catch (error) {
      if (!saved && slotId && (error.status === 404 || error.status === 409)) {
        if (byId('slot-id').value === String(slotId)) resetSlotForm();
        try { apply(await read()); ready = true; message(id, 'This time slot changed or was removed. Your availability has been refreshed.', true); }
        catch (reloadError) { requireReload(id, reloadError.message); }
      } else if (saved || error.uncertain || error.status === 401 || error.status === 403 || error.status >= 500) {
        requireReload(id, saved ? 'Your change was saved, but the updated availability could not be loaded. Retry loading before editing again.' : error.message);
      } else message(id, error.message, true);
    } finally { busy = false; controls(); }
  }
  function editSlot(slot) {
    if (!ready || busy) return;
    byId('slot-id').value = slot.slotId; byId('slot-day').value = slot.dayOfWeek;
    byId('slot-start').value = slot.startTime.slice(0, 5); byId('slot-end').value = slot.endTime.slice(0, 5);
    byId('slot-save').textContent = 'Save changes'; byId('slot-cancel').hidden = false;
    message('slot-message', ''); byId('slot-day').focus();
  }
  async function removeSlot(slot) {
    if (!ready || busy || !confirm(`Remove your ${names[slot.dayOfWeek]} time slot?`)) return;
    await mutate('slot-message', () => demo
      ? saveDemo({...data, slots: data.slots.filter(s => s.slotId !== slot.slotId)})
      : api(`/api/profiles/me/availability/slots/${slot.slotId}`, {method: 'DELETE'}), 'Time slot removed.',
    {slotId: slot.slotId, resetEdit: byId('slot-id').value === String(slot.slotId)});
  }
  byId('days-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!ready || busy) return;
    const days = [...document.querySelectorAll('.day-choice input:checked')].map(input => Number(input.value)).sort((a, b) => a - b);
    if (data.days.some(day => !days.includes(day))
      && !confirm('Removing a day also deletes all detailed time slots saved for that day. Continue?')) return;
    await mutate('days-message', () => demo
      ? saveDemo({...data, days, slots: data.slots.filter(s => days.includes(s.dayOfWeek))})
      : api('/api/profiles/me/availability/days', {method: 'PUT', body: JSON.stringify({days})}),
    'Your available days have been saved.', {returnsSnapshot: true});
  });
  byId('slot-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!ready || busy) return;
    const id = byId('slot-id').value;
    const body = {dayOfWeek: Number(byId('slot-day').value), startTime: byId('slot-start').value, endTime: byId('slot-end').value};
    if (!data.days.includes(body.dayOfWeek)) { message('slot-message', 'Choose one of your available days first.', true); return; }
    if (!body.startTime || !body.endTime || body.startTime >= body.endTime) { message('slot-message', 'The end time must be later than the start time.', true); return; }
    await mutate('slot-message', () => {
      if (demo) {
        const slots = id ? data.slots.map(s => s.slotId === Number(id) ? {...s, ...body} : s)
          : [...data.slots, {slotId: Math.max(0, ...data.slots.map(s => s.slotId)) + 1, ...body}];
        return saveDemo({...data, slots});
      }
      return api(id ? `/api/profiles/me/availability/slots/${id}` : '/api/profiles/me/availability/slots', {
        method: id ? 'PUT' : 'POST', body: JSON.stringify(body)});
    }, id ? 'Time slot updated.' : 'Time slot added.', {resetEdit: true, slotId: id ? Number(id) : null});
  });
  byId('slot-cancel').addEventListener('click', () => { if (ready && !busy) { resetSlotForm(); message('slot-message', ''); } });
  byId('availability-retry').addEventListener('click', load);
  byId('demo-hint').hidden = !demo;
  load();
})();
