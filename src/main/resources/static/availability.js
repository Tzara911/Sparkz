(() => {
  const byId = id => document.getElementById(id);
  const names = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  const demo = new URLSearchParams(location.search).get('demo') === '1';
  const storageKey = 'sparkz-demo-availability';
  let data = { days: [], slots: [], timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone };
  let session;
  const message = (id, text, error = false) => { byId(id).textContent = text; byId(id).classList.toggle('is-error', error); };
  function readDemo() {
    let hasSaved = false;
    try { const saved = JSON.parse(localStorage.getItem(storageKey)); if (saved && Array.isArray(saved.days) && Array.isArray(saved.slots)) { data = saved; hasSaved = true; } }
    catch (_) { /* Start with the built-in demo values. */ }
    if (!hasSaved) data = { days: [1, 3, 5], slots: [
      {slotId: 1, dayOfWeek: 1, startTime: '09:00', endTime: '11:00'},
      {slotId: 2, dayOfWeek: 3, startTime: '13:00', endTime: '15:00'},
      {slotId: 3, dayOfWeek: 5, startTime: '10:00', endTime: '12:00'}
    ], timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone };
  }
  function saveDemo() { localStorage.setItem(storageKey, JSON.stringify(data)); }
  async function api(path, options = {}) {
    const headers = { Accept: 'application/json', ...options.headers };
    const token = await window.sparkzAuth?.getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    else {
      if (!session) {
        const response = await fetch('/api/match-session');
        if (!response.ok) throw new Error('Please sign in before managing availability.');
        session = await response.json();
        window.dispatchEvent(new Event('sparkz:auth-changed'));
      }
      if (options.method) headers[session.csrfHeader] = session.csrfToken;
    }
    if (options.body) headers['Content-Type'] = 'application/json';
    const response = await fetch(path, { ...options, headers });
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (_) { /* Authentication responses may have no JSON body. */ }
      throw new Error(problem.detail || `Could not save availability (${response.status}).`);
    }
    return response.status === 204 ? null : response.json();
  }
  function populateDays() {
    const selected = new Set(data.days);
    document.querySelectorAll('.day-choice input').forEach(input => { input.checked = selected.has(Number(input.value)); });
    byId('slot-day').replaceChildren(new Option('Choose a day', ''));
    for (const day of data.days) byId('slot-day').add(new Option(names[day], String(day)));
  }
  function renderSlots() {
    const list = byId('slot-list');
    list.replaceChildren();
    const slots = [...data.slots].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime));
    byId('slot-count').textContent = `${slots.length} saved`;
    if (!slots.length) { const empty = document.createElement('p'); empty.className = 'empty-state'; empty.textContent = 'No time slots yet. Add a window above when you know you are free.'; list.append(empty); return; }
    for (const slot of slots) {
      const item = document.createElement('article'); item.className = 'slot-item';
      const details = document.createElement('div');
      const title = document.createElement('strong'); title.textContent = names[slot.dayOfWeek];
      const time = document.createElement('p'); time.textContent = `${formatTime(slot.startTime)} – ${formatTime(slot.endTime)}`;
      details.append(title, time);
      const actions = document.createElement('div'); actions.className = 'slot-actions';
      const edit = document.createElement('button'); edit.type = 'button'; edit.textContent = 'Edit'; edit.addEventListener('click', () => editSlot(slot));
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = 'Remove'; remove.className = 'delete-slot'; remove.addEventListener('click', () => removeSlot(slot));
      actions.append(edit, remove); item.append(details, actions); list.append(item);
    }
  }
  function formatTime(value) { const [hour, minute] = value.split(':').map(Number); return new Date(2000, 0, 1, hour, minute).toLocaleTimeString([], {hour: 'numeric', minute: '2-digit'}); }
  function resetSlotForm() { byId('slot-id').value = ''; byId('slot-form').reset(); byId('slot-save').innerHTML = 'Add time slot <span aria-hidden="true">+</span>'; byId('slot-cancel').hidden = true; }
  function editSlot(slot) { byId('slot-id').value = slot.slotId; byId('slot-day').value = slot.dayOfWeek; byId('slot-start').value = slot.startTime.slice(0, 5); byId('slot-end').value = slot.endTime.slice(0, 5); byId('slot-save').textContent = 'Save changes'; byId('slot-cancel').hidden = false; byId('slot-day').focus(); }
  async function removeSlot(slot) {
    if (!confirm(`Remove your ${names[slot.dayOfWeek]} time slot?`)) return;
    try { if (demo) { data.slots = data.slots.filter(row => row.slotId !== slot.slotId); saveDemo(); } else await api(`/api/profiles/me/availability/slots/${slot.slotId}`, {method: 'DELETE'}); await refresh(); message('slot-message', 'Time slot removed.'); }
    catch (error) { message('slot-message', error.message, true); }
  }
  async function refresh() {
    if (demo) readDemo(); else data = await api('/api/profiles/me/availability');
    populateDays(); renderSlots();
  }
  byId('days-form').addEventListener('submit', async event => {
    event.preventDefault();
    const days = [...document.querySelectorAll('.day-choice input:checked')].map(input => Number(input.value)).sort((a, b) => a - b);
    const removed = data.days.filter(day => !days.includes(day));
    if (removed.length && data.slots.some(slot => removed.includes(slot.dayOfWeek)) && !confirm('Removing a day also deletes all detailed time slots saved for that day. Continue?')) return;
    const button = event.target.querySelector('button'); button.disabled = true;
    try { if (demo) { data.days = days; data.slots = data.slots.filter(slot => days.includes(slot.dayOfWeek)); saveDemo(); } else data = await api('/api/profiles/me/availability/days', {method: 'PUT', body: JSON.stringify({days})}); populateDays(); renderSlots(); message('days-message', 'Your available days have been saved.'); }
    catch (error) { message('days-message', error.message, true); }
    finally { button.disabled = false; }
  });
  byId('slot-form').addEventListener('submit', async event => {
    event.preventDefault();
    const id = byId('slot-id').value;
    const body = {dayOfWeek: Number(byId('slot-day').value), startTime: byId('slot-start').value, endTime: byId('slot-end').value};
    if (!body.dayOfWeek) { message('slot-message', 'Choose one of your available days first.', true); return; }
    if (body.startTime >= body.endTime) { message('slot-message', 'The end time must be later than the start time.', true); return; }
    const button = byId('slot-save'); button.disabled = true;
    try {
      if (demo) {
        if (id) data.slots = data.slots.map(slot => slot.slotId === Number(id) ? {...slot, ...body} : slot);
        else data.slots.push({slotId: Math.max(0, ...data.slots.map(slot => slot.slotId)) + 1, ...body});
        saveDemo();
      } else await api(id ? `/api/profiles/me/availability/slots/${id}` : '/api/profiles/me/availability/slots', {method: id ? 'PUT' : 'POST', body: JSON.stringify(body)});
      resetSlotForm(); await refresh(); message('slot-message', id ? 'Time slot updated.' : 'Time slot added.');
    } catch (error) { message('slot-message', error.message, true); }
    finally { button.disabled = false; }
  });
  byId('slot-cancel').addEventListener('click', resetSlotForm);
  (async () => {
    if (demo) { byId('demo-hint').hidden = false; readDemo(); }
    try { await refresh(); }
    catch (error) { message('days-message', error.message, true); byId('slot-list').replaceChildren(Object.assign(document.createElement('p'), {className: 'empty-state', textContent: 'Sign in to load and manage your availability.'})); }
  })();
})();
