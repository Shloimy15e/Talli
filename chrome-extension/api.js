/* Shared by extension pages and service worker. Never log credentials. */
(function (root) {
  function normalizeServer(value) {
    let url;
    try { url = new URL(value.trim()); } catch { throw new Error('Enter a valid Talli server URL.'); }
    const local = ['localhost', '127.0.0.1'].includes(url.hostname);
    if (url.protocol !== 'https:' && !(local && url.protocol === 'http:')) {
      throw new Error('Use HTTPS, or HTTP for a local development server.');
    }
    if (url.username || url.password || url.search || url.hash || !/^\/*$/.test(url.pathname)) {
      throw new Error('Use the server origin only, without a path, query, or credentials.');
    }
    return url.origin;
  }

  async function request(serverUrl, apiToken, path, options = {}, timeoutMs = 12000) {
    const server = normalizeServer(serverUrl || '');
    if (!apiToken?.trim()) throw new Error('Add your API token in Settings.');
    const controller = new AbortController();
    const abort = () => controller.abort();
    options.signal?.addEventListener('abort', abort, { once: true });
    if (options.signal?.aborted) abort();
    let timedOut = false;
    const timeout = setTimeout(() => { timedOut = true; abort(); }, timeoutMs);
    try {
      const response = await fetch(server + path, {
        ...options, signal: controller.signal, credentials: 'omit', redirect: 'error',
        headers: { 'Content-Type': 'application/json', ...options.headers, Authorization: `Bearer ${apiToken.trim()}` }
      });
      if (!response.ok) {
        const detail = await response.json().catch(() => ({}));
        const message = response.status === 401 ? 'Your API token was rejected. Update it in Settings.'
          : response.status === 403 ? 'Access denied. Check this token’s permissions.'
          : typeof detail.error === 'string' ? detail.error : `Talli returned an error (${response.status}).`;
        throw new Error(message);
      }
      // Read within the timeout, including slow response bodies.
      const data = response.status === 204 ? null : await response.json();
      return { status: response.status, ok: true, json: async () => data };
    } catch (error) {
      if (error.name === 'AbortError' && !timedOut) throw error;
      if (timedOut || error instanceof TypeError) {
        const mutation = options.method && options.method !== 'GET';
        throw new Error((timedOut ? 'Talli took too long to respond.' : 'Cannot reach Talli.')
          + (mutation ? ' This action may have reached the server. Check Talli before trying again.' : ' Check your connection and retry.'));
      }
      throw error;
    } finally {
      clearTimeout(timeout);
      options.signal?.removeEventListener('abort', abort);
    }
  }
  const api = { normalizeServer, request };
  if (typeof module !== 'undefined') module.exports = api;
  root.TalliApi = api;
})(globalThis);
