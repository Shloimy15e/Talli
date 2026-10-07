const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function harness() {
  let settings = { serverUrl: 'https://old.mock', apiToken: 'old-token' };
  let onMessage;
  const badges = [];
  const titles = [];
  const requests = [];
  const context = vm.createContext({
    importScripts() {},
    chrome: {
      runtime: {
        onInstalled: { addListener() {} }, onStartup: { addListener() {} },
        onMessage: { addListener(listener) { onMessage = listener; } }
      },
      alarms: { create() {}, onAlarm: { addListener() {} } },
      storage: { local: { get: async () => ({ ...settings }), remove: async () => {} } },
      action: {
        setBadgeText: ({ text }) => badges.push(text),
        setBadgeBackgroundColor() {}, setTitle: ({ title }) => titles.push(title)
      }
    },
    TalliApi: {
      request: (serverUrl, apiToken) => new Promise((resolve, reject) => requests.push({ serverUrl, apiToken, resolve, reject }))
    }
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../background.js'), 'utf8'), context);
  return {
    badges, titles, requests,
    start: () => vm.runInContext('checkTimer()', context),
    change: next => { settings = next; onMessage({ type: 'connectionChanged' }); }
  };
}
const tick = () => new Promise(resolve => setImmediate(resolve));
const running = seconds => ({ ok: true, status: 200, json: async () => ({ elapsedSeconds: seconds }) });

for (const outcome of ['success', 'failure']) {
  test(`disconnect ignores an old in-flight ${outcome} and immediately checks cleared settings`, async () => {
    const app = harness();
    const pending = app.start();
    await tick();
    assert.equal(app.requests.length, 1);
    app.change({});
    if (outcome === 'failure') app.requests[0].reject(new Error('old server offline'));
    else app.requests[0].resolve(running(300));
    await pending;
    assert.equal(app.requests.length, 1); // queued check sees disconnected storage, sends no request
    assert.deepEqual(app.badges, ['', '']);
    assert.ok(app.titles.every(title => !title.includes('unable')));
  });

  test(`connection change ignores an old in-flight ${outcome} and polls the new connection without waiting for an alarm`, async () => {
    const app = harness();
    const pending = app.start();
    await tick();
    app.change({ serverUrl: 'https://new.mock', apiToken: 'new-token' });
    if (outcome === 'failure') app.requests[0].reject(new Error('old server offline'));
    else app.requests[0].resolve(running(300));
    await tick();
    assert.equal(app.requests.length, 2);
    assert.equal(app.requests[1].serverUrl, 'https://new.mock');
    assert.equal(app.requests[1].apiToken, 'new-token');
    assert.deepEqual(app.badges, ['']);
    app.requests[1].resolve(running(900));
    await pending;
    assert.deepEqual(app.badges, ['', '15m']);
    assert.ok(app.titles.every(title => !title.includes('unable')));
  });
}

test('a failure of the current connection still produces the actionable error badge', async () => {
  const app = harness();
  const pending = app.start();
  await tick();
  app.requests[0].reject(new Error('current server offline'));
  await pending;
  assert.deepEqual(app.badges, ['!']);
  assert.match(app.titles.at(-1), /unable to sync/);
});
