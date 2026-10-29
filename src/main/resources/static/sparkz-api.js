/* #5 supplies sparkzAuth.getAccessToken() with the current Supabase token.
   Only local/test profiles expose the Basic-authenticated match-session endpoint. */
(() => {
  let session;
  window.sparkzApi = async (path, options = {}) => {
    const headers = { Accept: 'application/json', ...options.headers };
    const token = await window.sparkzAuth?.getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    else {
      if (!session) {
        const response = await fetch('/api/match-session');
        if (!response.ok) throw new Error('Please sign in to discover people and manage requests.');
        session = await response.json();
      }
      if (options.method) headers[session.csrfHeader] = session.csrfToken;
    }
    if (options.body) headers['Content-Type'] = 'application/json';
    const response = await fetch(path, { ...options, headers });
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (_) { /* Authentication may return an empty body. */ }
      const error = new Error(problem.detail || `Request failed (${response.status}). Please refresh and try again.`);
      error.status = response.status;
      throw error;
    }
    return response.json();
  };
})();
