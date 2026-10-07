const serverUrlInput = document.getElementById('serverUrl');
const apiTokenInput = document.getElementById('apiToken');
const feedback = document.getElementById('feedback');
const connectionStatus = document.getElementById('connectionStatus');
let busy = false;
let revision = 0;
chrome.storage.local.get(['serverUrl', 'apiToken']).then(data => {
  if (!revision) {
    serverUrlInput.value = data.serverUrl || '';
    apiTokenInput.value = data.apiToken || '';
  }
});
// Remove credentials stored as unsaved drafts by earlier extension versions.
chrome.storage.local.remove(['draftServerUrl', 'draftApiToken']);
[serverUrlInput, apiTokenInput].forEach(input => input.addEventListener('input', () => {
  revision++;
  connectionStatus.textContent = '';
}));
function showFeedback(message, type) {
  feedback.textContent = message;
  feedback.className = `feedback ${type}`;
}
function lock(value) {
  busy = value;
  ['saveBtn', 'testBtn', 'disconnectBtn'].forEach(id => document.getElementById(id).disabled = value);
}
async function connect(save) {
  if (busy) return;
  let serverUrl;
  const apiToken = apiTokenInput.value.trim();
  try {
    serverUrl = TalliApi.normalizeServer(serverUrlInput.value);
    if (!apiToken) throw new Error('Enter your API token.');
  } catch (error) { showFeedback(error.message, 'error'); return; }
  // Request only this origin, directly from the explicit button gesture.
  const permission = chrome.permissions.request({ origins: [serverUrl + '/*'] });
  const version = revision;
  lock(true);
  showFeedback('Checking your connection…', 'success');
  try {
    if (!await permission) throw new Error('Allow access to this Talli server to connect.');
    const response = await TalliApi.request(serverUrl, apiToken, '/api/v1/projects');
    const projects = await response.json();
    if (!Array.isArray(projects)) throw new Error('This server did not return a Talli project list.');
    if (version !== revision) { showFeedback('Details changed. Check the current values again.', 'error'); return; }
    connectionStatus.textContent = `Connected · ${projects.length} active project${projects.length === 1 ? '' : 's'}`;
    if (save) {
      await chrome.storage.local.set({ serverUrl, apiToken });
      chrome.runtime.sendMessage({ type: 'connectionChanged' });
      serverUrlInput.value = serverUrl;
      showFeedback('Connection saved. Return to Quick capture to start tracking.', 'success');
    } else showFeedback('Connection verified. Save to use these details.', 'success');
  } catch (error) {
    if (version === revision) showFeedback(error.message, 'error');
  } finally { lock(false); }
}
document.getElementById('testBtn').addEventListener('click', () => connect(false));
document.getElementById('saveBtn').addEventListener('click', () => connect(true));
document.getElementById('backBtn').addEventListener('click', () => { window.location.href = 'popup.html'; });
document.getElementById('disconnectBtn').addEventListener('click', async () => {
  if (busy) return;
  lock(true);
  try {
    const data = await chrome.storage.local.get(['serverUrl']);
    await chrome.storage.local.remove(['serverUrl', 'apiToken', 'draftServerUrl', 'draftApiToken']);
    if (data.serverUrl) await chrome.permissions.remove({ origins: [TalliApi.normalizeServer(data.serverUrl) + '/*'] });
    revision++;
    serverUrlInput.value = ''; apiTokenInput.value = ''; connectionStatus.textContent = '';
    chrome.runtime.sendMessage({ type: 'connectionChanged' });
    showFeedback('Disconnected. Your saved API token has been removed.', 'success');
  } catch (error) { showFeedback(error.message, 'error'); }
  finally { lock(false); }
});
