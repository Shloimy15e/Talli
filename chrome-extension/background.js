// Background service worker — polls for running timer and updates badge.

chrome.runtime.onInstalled.addListener(() => {
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
  if (message.type === 'timerStarted' || message.type === 'timerStopped') {
    checkTimer();
  }
});

async function checkTimer() {
  try {
    const { serverUrl, apiToken } = await chrome.storage.local.get(['serverUrl', 'apiToken']);
    if (!serverUrl || !apiToken) {
      chrome.action.setBadgeText({ text: '' });
      return;
    }

    const res = await fetch(`${serverUrl}/api/v1/time/current`, {
      headers: { 'Authorization': `Bearer ${apiToken}` }
    });

    if (res.status === 204) {
      // No running timer
      chrome.action.setBadgeText({ text: '' });
      return;
    }

    if (res.ok) {
      const timer = await res.json();
      // Use server-computed elapsedSeconds — avoids client/server clock mismatch.
      const diffMinutes = Math.floor((timer.elapsedSeconds || 0) / 60);
      const h = Math.floor(diffMinutes / 60);
      const m = diffMinutes % 60;

      const badgeText = h > 0 ? `${h}:${String(m).padStart(2, '0')}` : `${m}m`;
      chrome.action.setBadgeText({ text: badgeText });
      chrome.action.setBadgeBackgroundColor({ color: '#ea7c28' });
    } else {
      chrome.action.setBadgeText({ text: '' });
    }
  } catch {
    chrome.action.setBadgeText({ text: '' });
  }
}
