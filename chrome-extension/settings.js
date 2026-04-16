const serverUrlInput = document.getElementById('serverUrl');
const apiTokenInput = document.getElementById('apiToken');
const saveBtn = document.getElementById('saveBtn');
const testBtn = document.getElementById('testBtn');
const backBtn = document.getElementById('backBtn');
const feedback = document.getElementById('feedback');
const connectionStatus = document.getElementById('connectionStatus');

// Load saved settings
chrome.storage.local.get(['serverUrl', 'apiToken'], (data) => {
  if (data.serverUrl) serverUrlInput.value = data.serverUrl;
  if (data.apiToken) apiTokenInput.value = data.apiToken;
});

saveBtn.addEventListener('click', () => {
  const serverUrl = serverUrlInput.value.replace(/\/+$/, ''); // trim trailing slashes
  const apiToken = apiTokenInput.value.trim();

  if (!serverUrl || !apiToken) {
    showFeedback('Both fields are required.', 'error');
    return;
  }

  chrome.storage.local.set({ serverUrl, apiToken }, () => {
    showFeedback('Settings saved.', 'success');
  });
});

testBtn.addEventListener('click', async () => {
  const serverUrl = serverUrlInput.value.replace(/\/+$/, '');
  const apiToken = apiTokenInput.value.trim();

  if (!serverUrl || !apiToken) {
    showFeedback('Enter both fields first.', 'error');
    return;
  }

  connectionStatus.textContent = 'Testing...';
  connectionStatus.style.background = 'var(--bg)';
  connectionStatus.style.color = 'var(--text-muted)';

  try {
    const res = await fetch(`${serverUrl}/api/v1/projects`, {
      headers: { 'Authorization': `Bearer ${apiToken}` }
    });

    if (res.ok) {
      const projects = await res.json();
      connectionStatus.textContent = `Connected — ${projects.length} active project${projects.length !== 1 ? 's' : ''}`;
      connectionStatus.style.background = 'var(--success-bg)';
      connectionStatus.style.color = 'var(--success)';
    } else if (res.status === 401) {
      connectionStatus.textContent = 'Invalid API token';
      connectionStatus.style.background = 'var(--danger-bg)';
      connectionStatus.style.color = 'var(--danger)';
    } else {
      connectionStatus.textContent = `Server error (${res.status})`;
      connectionStatus.style.background = 'var(--danger-bg)';
      connectionStatus.style.color = 'var(--danger)';
    }
  } catch (err) {
    connectionStatus.textContent = 'Cannot reach server';
    connectionStatus.style.background = 'var(--danger-bg)';
    connectionStatus.style.color = 'var(--danger)';
  }
});

backBtn.addEventListener('click', () => {
  window.location.href = 'popup.html';
});

function showFeedback(message, type) {
  feedback.textContent = message;
  feedback.className = `feedback ${type}`;
  setTimeout(() => { feedback.className = 'feedback'; }, 3000);
}
