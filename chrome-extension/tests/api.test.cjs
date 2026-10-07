const { test } = require('node:test');
const assert = require('node:assert/strict');
const { normalizeServer, request } = require('../api.js');

test('server validation keeps token-bearing requests on HTTPS origins or local development', () => {
  assert.equal(normalizeServer(' https://talli.example.com/// '), 'https://talli.example.com');
  assert.equal(normalizeServer('http://localhost:8080'), 'http://localhost:8080');
  for (const value of ['http://example.com', 'https://user:pass@example.com', 'https://example.com/api', 'https://example.com?token=secret', 'javascript:alert(1)']) {
    assert.throws(() => normalizeServer(value));
  }
});

test('request rejects auth errors and non-JSON server errors without exposing token', async () => {
  global.fetch = async () => ({ ok: false, status: 401, json: async () => { throw new Error('HTML'); } });
  await assert.rejects(request('https://talli.example.com', 'private', '/api/v1/projects'), /token was rejected/);
  global.fetch = async () => ({ ok: false, status: 502, json: async () => { throw new Error('HTML'); } });
  await assert.rejects(request('https://talli.example.com', 'private', '/api/v1/projects'), /502/);
});

test('timeout aborts mutations without automatic replay and flags uncertain outcome', async () => {
  let calls = 0;
  global.fetch = (_, options) => new Promise((resolve, reject) => {
    calls++;
    options.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
  });
  await assert.rejects(request('https://talli.example.com', 'private', '/api/v1/time/start', { method: 'POST' }, 5), /may have reached the server/);
  assert.equal(calls, 1);
});

test('caller cancellation propagates AbortError and no redirect or cookies are sent', async () => {
  const controller = new AbortController();
  global.fetch = (_, options) => new Promise((resolve, reject) => {
    assert.equal(options.credentials, 'omit');
    assert.equal(options.redirect, 'error');
    options.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
  });
  const pending = request('https://talli.example.com', 'private', '/api/v1/projects', { signal: controller.signal });
  controller.abort();
  await assert.rejects(pending, { name: 'AbortError' });
});

test('204 responses do not parse a body', async () => {
  global.fetch = async () => ({ ok: true, status: 204 });
  const response = await request('https://talli.example.com', 'private', '/api/v1/time/current');
  assert.equal(await response.json(), null);
});
