const serverUrlInput = document.getElementById('serverUrl');
const apiTokenInput = document.getElementById('apiToken');
const saveBtn = document.getElementById('saveBtn');
const testBtn = document.getElementById('testBtn');
const backBtn = document.getElementById('backBtn');
const feedback = document.getElementById('feedback');
const connectionStatus = document.getElementById('connectionStatus');

// Load persisted values (saved values + unsaved drafts)
chrome.storage.local.get(['serverUrl', 'apiToken', 'draftServerUrl', 'draftApiToken'], (data) => {
  // Prefer drafts if they exist (user was in the middle of editing)
  if (data.draftServerUrl !== undefined) serverUrlInput.value = data.draftServerUrl;
  else if (data.serverUrl) serverUrlInput.value = data.serverUrl;

  if (data.draftApiToken !== undefined) apiTokenInput.value = data.draftApiToken;
  else if (data.apiToken) apiTokenInput.value = data.apiToken;
});

// Persist drafts as user types — survives popup close (e.g. switching tabs to copy token).
serverUrlInput.addEventListener('input', () => {
  chrome.storage.local.set({ draftServerUrl: serverUrlInput.value });
});
apiTokenInput.addEventListener('input', () => {
  chrome.storage.local.set({ draftApiToken: apiTokenInput.value });
});

saveBtn.addEventListener('click', () => {
  const serverUrl = serverUrlInput.value.replace(/\/+$/, ''); // trim trailing slashes
  const apiToken = apiTokenInput.value.trim();

  if (!serverUrl || !apiToken) {
    showFeedback('Both fields are required.', 'error');
    return;
  }

  chrome.storage.local.set({ serverUrl, apiToken }, () => {
    // Clear drafts — they've been committed
    chrome.storage.local.remove(['draftServerUrl', 'draftApiToken']);
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
