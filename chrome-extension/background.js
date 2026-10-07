importScripts('api.js');
let checking = false;
let checkAgain = false;
let connectionRevision = 0;
// Background service worker — polls for running timer and updates badge.

chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.remove(['draftServerUrl', 'draftApiToken']);
  chrome.alarms.create('checkTimer', { periodInMinutes: 1 });
  checkTimer(); // check immediately on install
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'checkTimer') {
    checkTimer();
  }
});

// Messages from popup when timer starts/stops
chrome.runtime.onMessage.addListener((message) => {
  if (message.type === 'connectionChanged') {
    connectionRevision++;
    chrome.action.setBadgeText({ text: '' });
    chrome.action.setTitle({ title: 'Talli Quick capture' });
  }
  if (message.type === 'timerStarted' || message.type === 'timerStopped' || message.type === 'connectionChanged') {
    checkTimer();
  }
});

async function checkTimer() {
  if (checking) { checkAgain = true; return; }
  checking = true;
  const revision = connectionRevision;
  let serverUrl;
  let apiToken;
  try {
    chrome.action.setTitle({ title: 'Talli Quick capture' });
    ({ serverUrl, apiToken } = await chrome.storage.local.get(['serverUrl', 'apiToken']));
    if (revision !== connectionRevision) return;
    if (!serverUrl || !apiToken) {
      chrome.action.setBadgeText({ text: '' });
      return;
    }

    const res = await TalliApi.request(serverUrl, apiToken, '/api/v1/time/current');

    const timer = res.status === 204 ? null : await res.json();
    const latest = await chrome.storage.local.get(['serverUrl', 'apiToken']);
    if (revision !== connectionRevision || latest.serverUrl !== serverUrl || latest.apiToken !== apiToken) return;
    if (res.status === 204) {
      // No running timer
      chrome.action.setBadgeText({ text: '' });
      return;
    }

    if (res.ok) {
      // Use server-computed elapsedSeconds — avoids client/server clock mismatch.
      const diffMinutes = Math.floor((timer.elapsedSeconds || 0) / 60);
      const h = Math.floor(diffMinutes / 60);
      const m = diffMinutes % 60;

      const badgeText = h > 0 ? `${h}:${String(m).padStart(2, '0')}` : `${m}m`;
      chrome.action.setBadgeText({ text: badgeText });
      chrome.action.setBadgeBackgroundColor({ color: '#F97316' });
    } else {
      chrome.action.setBadgeText({ text: '' });
    }
  } catch {
    const latest = await chrome.storage.local.get(['serverUrl', 'apiToken']).catch(() => null);
    if (revision !== connectionRevision || !latest || latest.serverUrl !== serverUrl || latest.apiToken !== apiToken) return;
    chrome.action.setBadgeText({ text: '!' });
    chrome.action.setBadgeBackgroundColor({ color: '#9e4033' });
    chrome.action.setTitle({ title: 'Talli: unable to sync timer. Open Quick capture to retry.' });
  } finally {
    checking = false;
    if (checkAgain) {
      checkAgain = false;
      await checkTimer();
    }
  }
}

chrome.runtime.onStartup.addListener(() => { chrome.alarms.create('checkTimer', { periodInMinutes: 1 }); checkTimer(); });
