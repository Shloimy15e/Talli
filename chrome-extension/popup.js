// --- API helper ---

async function apiFetch(path, options = {}) {
  const { serverUrl, apiToken } = await chrome.storage.local.get(['serverUrl', 'apiToken']);
  if (!serverUrl || !apiToken) throw new Error('Connect your Talli server and API token in Settings.');
  const response = await TalliApi.request(serverUrl, apiToken, path, options);
  document.getElementById('connectionLabel').textContent = 'Connected';
  return response;
}
function showActionError(error) {
  const feedback = document.getElementById('actionFeedback');
  feedback.textContent = error.message || String(error);
  feedback.className = 'feedback error';
  document.getElementById('connectionLabel').textContent = 'Check connection';
}
let timerBusy = false;
let expenseBusy = false;
let projectBusy = false;
let initialized = false;
let initGeneration = 0;
// --- State ---

let projects = [];
let clients = [];
let selectedProjectId = null;
let currentTimer = null;
let elapsedInterval = null;

// --- Init ---

// Settings button is always wired up first — works regardless of API state.
document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('settingsBtn').addEventListener('click', () => {
    window.location.href = 'settings.html';
  });
  document.getElementById('errorSettingsBtn').addEventListener('click', () => {
    window.location.href = 'settings.html';
  });
  document.getElementById('retryBtn').addEventListener('click', () => {
    document.getElementById('errorState').style.display = 'none';
    document.getElementById('loading').style.display = 'block';
    initApp();
  });

  document.getElementById('refreshBtn').addEventListener('click', () => {
    if (!timerBusy) initApp();
  });
  document.getElementById('recentSearch').addEventListener('input', renderProjectList);
  document.getElementById('expDate').value = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  initApp();
});

async function initApp() {
  if (timerBusy) return;
  const generation = ++initGeneration;
  document.getElementById('refreshBtn').disabled = true;
  document.getElementById('connectionLabel').textContent = 'Refreshing�';
  if (elapsedInterval) clearInterval(elapsedInterval);
  try {
    await loadProjects(generation);
    await loadTimer(generation);
    if (generation !== initGeneration) return;
    document.getElementById('loading').style.display = 'none';
    document.getElementById('errorState').style.display = 'none';
    document.getElementById('app').style.display = 'block';
  } catch (err) {
    if (generation !== initGeneration) return;
    document.getElementById('loading').style.display = 'none';
    document.getElementById('app').style.display = 'none';
    document.getElementById('errorMessage').textContent = String(err.message || err);
    document.getElementById('errorState').style.display = 'flex';
    document.getElementById('refreshBtn').disabled = false;
    return;
  }

  document.getElementById('refreshBtn').disabled = false;
  if (!initialized) {
  initialized = true;
  setupModes();
  setupTimerBar();
  setupProjectPicker();
  setupExpenseForm();
  setupCreateProject();
  }
  renderProjectList();
}

// --- Mode switching ---

function setupModes() {
  document.querySelectorAll('.mode-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      document.getElementById('timerPanel').style.display = btn.dataset.mode === 'timer' ? 'block' : 'none';
      document.getElementById('expensePanel').style.display = btn.dataset.mode === 'expense' ? 'block' : 'none';
    });
  });
}

// --- Projects ---

async function loadProjects(generation = initGeneration) {
  const res = await apiFetch('/api/v1/projects');
  const data = await res.json();
  if (generation !== initGeneration) return;
  projects = data;
  if (!Array.isArray(projects)) throw new Error('Talli returned an invalid project list.');

  // Populate expense project dropdown (clear first since this runs on retry too)
  const expSelect = document.getElementById('expProject');
  expSelect.innerHTML = '<option value="">None</option>';
  projects.forEach(p => {
    const opt = document.createElement('option');
    opt.value = p.id;
    opt.textContent = p.clientName ? `${p.name} (${p.clientName})` : p.name;
    expSelect.appendChild(opt);
  });
}

async function loadClients() {
  const res = await apiFetch('/api/v1/clients');
  clients = await res.json();
  if (!Array.isArray(clients)) throw new Error('Talli returned an invalid client list.');
}

// --- Searchable project picker ---

function setupProjectPicker() {
  const btn = document.getElementById('projectPickerBtn');
  const dropdown = document.getElementById('projectDropdown');
  const search = document.getElementById('projectSearch');

  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const isOpen = dropdown.classList.contains('open');
    dropdown.classList.toggle('open');
    btn.setAttribute('aria-expanded', String(!isOpen));
    if (!isOpen) {
      search.value = '';
      renderProjectDropdown('');
      setTimeout(() => search.focus(), 0);
    }
  });

  search.addEventListener('input', () => {
    renderProjectDropdown(search.value);
  });

  search.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      dropdown.classList.remove('open');
      btn.setAttribute('aria-expanded', 'false');
    }
  });

  // Close on outside click
  document.addEventListener('pointerdown', (e) => {
    if (!document.getElementById('projectPicker').contains(e.target) && !e.target.closest('.ui-select-popup')) {
      dropdown.classList.remove('open');
      btn.setAttribute('aria-expanded', 'false');
    }
  });
}

function renderProjectDropdown(query) {
  const list = document.getElementById('projectList');
  const q = query.toLowerCase();
  const filtered = projects.filter(p =>
    p.name.toLowerCase().includes(q) ||
    (p.clientName && p.clientName.toLowerCase().includes(q))
  );

  list.innerHTML = filtered.map(p => `
    <button type="button" class="project-option ${p.id === selectedProjectId ? 'selected' : ''}" data-id="${p.id}">
      <span class="project-option-name">${escapeHtml(p.name)}</span>
      ${p.clientName ? `<span class="project-option-client">${escapeHtml(p.clientName)}</span>` : ''}
    </button>
  `).join('');

  if (filtered.length === 0) {
    list.innerHTML = '<div style="padding:10px;text-align:center;color:#94a3b8;font-size:12px">No matches</div>';
  }

  list.querySelectorAll('.project-option').forEach(opt => {
    opt.addEventListener('click', () => {
      selectProject(parseInt(opt.dataset.id));
      document.getElementById('projectDropdown').classList.remove('open');
      document.getElementById('projectPickerBtn').setAttribute('aria-expanded', 'false');
    });
  });
}

function selectProject(projectId) {
  selectedProjectId = projectId;
  const project = projects.find(p => p.id === projectId);
  const btn = document.getElementById('projectPickerBtn');
  const label = document.getElementById('projectPickerLabel');

  if (project) {
    label.textContent = project.name;
    btn.classList.add('has-value');
  } else {
    label.textContent = 'Choose a project';
    btn.classList.remove('has-value');
  }
}

// --- Timer ---

// Baseline for elapsed-time display — captures the server-computed elapsed
// at fetch time and the client timestamp at that moment. Eliminates any
// dependency on server/client clock sync or timezone alignment.
let elapsedBaselineSeconds = 0;
let elapsedBaselineClientMs = 0;

async function loadTimer(generation = initGeneration) {
  const res = await apiFetch('/api/v1/time/current');
  if (generation !== initGeneration) return;
  if (res.status === 204) {
    currentTimer = null;
    showTimerStopped();
  } else {
    currentTimer = await res.json();
    elapsedBaselineSeconds = currentTimer.elapsedSeconds || 0;
    elapsedBaselineClientMs = Date.now();
    showTimerRunning();
  }
}

function showTimerRunning() {
  document.getElementById('timerStopped').style.display = 'none';
  document.getElementById('timerRunning').style.display = 'grid';
  document.getElementById('timerBar').classList.add('running');

  const runningDesc = document.getElementById('runningDesc');
  runningDesc.value = currentTimer.description || '';
  const project = projects.find(project => project.id === currentTimer.projectId);
  document.getElementById('runningProject').textContent = currentTimer.projectName || project?.name || 'Project unavailable';
  document.getElementById('runningClient').textContent = project ? (project.clientName || 'No client assigned') : 'Client details unavailable';

  updateElapsed();
  if (elapsedInterval) clearInterval(elapsedInterval);
  elapsedInterval = setInterval(updateElapsed, 1000);
  renderProjectList();
}

function showTimerStopped() {
  if (elapsedInterval) {
    clearInterval(elapsedInterval);
    elapsedInterval = null;
  }
  document.getElementById('timerRunning').style.display = 'none';
  document.getElementById('timerStopped').style.display = 'grid';
  document.getElementById('timerBar').classList.remove('running');
  renderProjectList();
}

function setupTimerBar() {
  document.getElementById('startBtn').addEventListener('click', startTimer);
  document.getElementById('timerDesc').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') startTimer();
  });
  document.getElementById('stopBtn').addEventListener('click', stopTimer);

  const runningDesc = document.getElementById('runningDesc');
  runningDesc.addEventListener('blur', saveRunningDescription);
  runningDesc.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      runningDesc.blur();
    } else if (e.key === 'Escape') {
      runningDesc.value = currentTimer ? (currentTimer.description || '') : '';
      runningDesc.blur();
    }
  });
}

async function saveRunningDescription() {
  if (!currentTimer || timerBusy) return;
  const timerId = currentTimer.id;
  const next = document.getElementById('runningDesc').value.trim();
  const prev = currentTimer.description || '';
  if (next === prev) return;

  try {
    const res = await apiFetch(`/api/v1/time/${currentTimer.id}/description`, {
      method: 'POST',
      body: JSON.stringify({ description: next || null })
    });
    if (res.ok) {
      if (currentTimer?.id === timerId) currentTimer.description = next;
    } else {
      document.getElementById('runningDesc').value = prev;
    }
  } catch (error) {
    showActionError(error);
    if (currentTimer?.id === timerId) document.getElementById('runningDesc').value = prev;
  }
}

function updateElapsed() {
  if (!currentTimer) return;
  // Server-authoritative baseline + local increment. No clock sync required.
  const localDelta = Math.floor((Date.now() - elapsedBaselineClientMs) / 1000);
  const diff = Math.max(0, elapsedBaselineSeconds + localDelta);
  const h = Math.floor(diff / 3600);
  const m = Math.floor((diff % 3600) / 60);
  const s = diff % 60;
  document.getElementById('elapsed').textContent = `${pad(h)}:${pad(m)}:${pad(s)}`;
}

async function startTimer() {
  if (timerBusy || currentTimer) return;
  if (!selectedProjectId) {
    // Open the project picker
    document.getElementById('projectPickerBtn').click();
    return;
  }

  const description = document.getElementById('timerDesc').value.trim();
  const btn = document.getElementById('startBtn');
  timerBusy = true;
  setTimerBusy(true);

  try {
    const res = await apiFetch('/api/v1/time/start', {
      method: 'POST',
      body: JSON.stringify({ projectId: selectedProjectId, description: description || null })
    });

    if (res.ok) {
      currentTimer = await res.json();
      // Fresh start — elapsed is 0 from the client's perspective.
      elapsedBaselineSeconds = 0;
      elapsedBaselineClientMs = Date.now();
      document.getElementById('timerDesc').value = '';
      showTimerRunning();
      chrome.runtime.sendMessage({ type: 'timerStarted' });
    }
  } catch (error) { showActionError(error); }
  timerBusy = false;
  setTimerBusy(false);
}

async function stopTimer() {
  if (!currentTimer || timerBusy) return;
  const btn = document.getElementById('stopBtn');
  timerBusy = true;
  setTimerBusy(true);

  try {
    const res = await apiFetch(`/api/v1/time/${currentTimer.id}/stop`, { method: 'POST' });
    if (res.ok) {
      currentTimer = null;
      showTimerStopped();
      chrome.runtime.sendMessage({ type: 'timerStopped' });
    }
  } catch (error) { showActionError(error); }
  timerBusy = false;
  setTimerBusy(false);
}

async function quickStart(projectId) {
  if (timerBusy || currentTimer) return;
  selectProject(projectId);
  await startTimer();
}
function setTimerBusy(busy) {
  document.querySelectorAll('#startBtn, #stopBtn, .recent-entry-play').forEach(button => {
    button.disabled = busy || (!!currentTimer && button.dataset.running === 'false');
  });
  document.getElementById('runningDesc').disabled = busy;
  if (busy) document.getElementById('actionFeedback').className = 'feedback';
  document.getElementById('refreshBtn').disabled = busy;
}

// --- Project list (timer panel) ---

function renderProjectList() {
  const container = document.getElementById('recentEntries');
  const empty = document.getElementById('noEntries');

  if (projects.length === 0) {
    container.style.display = 'none';
    empty.style.display = 'block';
    return;
  }

  empty.style.display = 'none';
  container.style.display = 'block';

  // Projects already come ordered by most recent time entry from the API
  const runningProjectId = currentTimer ? currentTimer.projectId : null;
  const playIcon = '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,3 20,12 6,21"/></svg>';
  const stopIcon = '<svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>';

  const query = document.getElementById('recentSearch').value.toLowerCase();
  const visible = projects.filter(p => `${p.name} ${p.clientName || ''}`.toLowerCase().includes(query));
  container.innerHTML = visible.map(p => {
    const isRunning = p.id === runningProjectId;
    return `
    <div class="recent-entry" data-project-id="${p.id}">
      <button type="button" class="recent-entry-info" aria-label="Select ${escapeHtml(p.name)}">
        <div class="recent-entry-name">${escapeHtml(p.name)}</div>
        ${p.clientName ? `<div class="recent-entry-client">${escapeHtml(p.clientName)}</div>` : ''}
      </button>
      <button type="button" aria-label="${isRunning ? 'Stop timer' : 'Start timer'}: ${escapeHtml(p.name)}" ${currentTimer && !isRunning || timerBusy ? 'disabled' : ''} class="recent-entry-play${isRunning ? ' running' : ''}" data-pid="${p.id}" data-running="${isRunning}" title="${isRunning ? 'Stop timer' : 'Start timer'}">
        ${isRunning ? 'Stop' : 'Start'}
      </button>
    </div>
    `;
  }).join('');

  if (!visible.length) container.textContent = 'No matching projects.';
  // Play button starts timer, running button stops it
  container.querySelectorAll('.recent-entry-play').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (btn.dataset.running === 'true') {
        stopTimer();
      } else {
        quickStart(parseInt(btn.dataset.pid));
      }
    });
  });

  // Clicking row selects project in the timer bar and prefills its last description
  container.querySelectorAll('.recent-entry-info').forEach(row => {
    row.addEventListener('click', () => {
      const projectId = parseInt(row.parentElement.dataset.projectId);
      selectProject(projectId);
      const project = projects.find(p => p.id === projectId);
      const descInput = document.getElementById('timerDesc');
      if (project && project.lastDescription && !descInput.value.trim()) {
        descInput.value = project.lastDescription;
      }
      descInput.focus();
    });
  });
}

// --- Expense ---

function setupExpenseForm() {
  document.getElementById('expenseForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (expenseBusy) return;
    const feedback = document.getElementById('expenseFeedback');
    const btn = e.target.querySelector('button[type="submit"]');

    const projectId = document.getElementById('expProject').value;
    const amount = document.getElementById('expAmount').value;
    const category = document.getElementById('expCategory').value;
    const vendor = document.getElementById('expVendor').value;
    const description = document.getElementById('expDescription').value;

    if (!Number.isFinite(Number(amount)) || Number(amount) <= 0) {
      feedback.textContent = 'Enter an amount greater than zero.';
      feedback.className = 'feedback error';
      return;
    }
    expenseBusy = true;
    btn.disabled = true;
    btn.textContent = 'Saving...';

    try {
      const res = await apiFetch('/api/v1/expenses', {
        method: 'POST',
        body: JSON.stringify({
          projectId: projectId ? parseInt(projectId) : null,
          amount: parseFloat(amount),
          category,
          currency: document.getElementById('expCurrency').value,
          incurredOn: document.getElementById('expDate').value || null,
          billable: document.getElementById('expBillable').checked,
          vendor: vendor || null,
          description: description || null
        })
      });

      if (res.ok) {
        feedback.textContent = 'Expense saved!';
        feedback.className = 'feedback success';
        document.getElementById('expAmount').value = '';
        document.getElementById('expVendor').value = '';
        document.getElementById('expDescription').value = '';
        setTimeout(() => { feedback.className = 'feedback'; }, 3000);
      } else {
        const err = await res.json();
        feedback.textContent = err.error || 'Failed to save';
        feedback.className = 'feedback error';
      }
    } catch (error) {
      feedback.textContent = error.message;
      feedback.className = 'feedback error';
    }

    expenseBusy = false;
    btn.disabled = false;
    btn.textContent = 'Save expense';
  });
}

// --- Create project (inline in the project picker dropdown) ---

const BILLING_FREQUENCIES = {
  hourly: [
    { value: 'weekly', label: 'Week' },
    { value: 'biweekly', label: 'Two Weeks' },
    { value: 'monthly', label: 'Month' }
  ],
  retainer: [
    { value: 'weekly', label: 'Week' },
    { value: 'biweekly', label: 'Two Weeks' },
    { value: 'monthly', label: 'Month' }
  ],
  fixed: [
    { value: 'upfront', label: 'Upfront (one-time at start)' },
    { value: 'delivery', label: 'On delivery (one-time at end)' },
    { value: 'milestone', label: 'By milestone' }
  ]
};

function setupCreateProject() {
  const openBtn = document.getElementById('openCreateProject');
  const backBtn = document.getElementById('backToList');
  const submitBtn = document.getElementById('submitNewProject');
  const listView = document.getElementById('projectListView');
  const createView = document.getElementById('projectCreateView');
  const dropdown = document.getElementById('projectDropdown');
  const rateTypeSelect = document.getElementById('newProjectRateType');
  const freqLabel = document.getElementById('newProjectFreqLabel');

  openBtn.addEventListener('click', async () => {
    if (clients.length === 0) {
      try {
        await loadClients();
      } catch (err) {
        showCreateProjectError(err.message || String(err));
        return;
      }
    }
    populateClientDropdown();
    updateFrequencyOptions(rateTypeSelect.value);
    listView.style.display = 'none';
    createView.style.display = 'block';
    dropdown.classList.add('create-mode');
    document.getElementById('newProjectName').focus();
  });

  backBtn.addEventListener('click', () => {
    createView.style.display = 'none';
    listView.style.display = 'block';
    dropdown.classList.remove('create-mode');
    resetCreateProjectForm();
  });

  submitBtn.addEventListener('click', submitNewProject);

  rateTypeSelect.addEventListener('change', () => {
    updateFrequencyOptions(rateTypeSelect.value);
    freqLabel.textContent = rateTypeSelect.value === 'fixed' ? 'Invoice' : 'Invoice every';
  });

  // Submit on Enter from text/number fields (but not selects, which have their own Enter behavior)
  ['newProjectName', 'newProjectRate'].forEach(id => {
    document.getElementById(id).addEventListener('keydown', (e) => {
      if (e.key === 'Enter') submitNewProject();
    });
  });
}

function updateFrequencyOptions(rateType) {
  const select = document.getElementById('newProjectFrequency');
  const options = BILLING_FREQUENCIES[rateType] || [];
  select.innerHTML = options.map(o => `<option value="${o.value}">${o.label}</option>`).join('');
}

function populateClientDropdown() {
  const select = document.getElementById('newProjectClient');
  select.innerHTML = '<option value="">Select client...</option>';
  clients.forEach(c => {
    const opt = document.createElement('option');
    opt.value = c.id;
    opt.textContent = c.name;
    select.appendChild(opt);
  });
}

async function submitNewProject() {
  if (projectBusy) return;
  const name = document.getElementById('newProjectName').value.trim();
  const clientId = document.getElementById('newProjectClient').value;
  const rate = document.getElementById('newProjectRate').value;
  const rateType = document.getElementById('newProjectRateType').value;
  const currency = document.getElementById('newProjectCurrency').value;
  const billingFrequency = document.getElementById('newProjectFrequency').value;
  const billable = document.getElementById('newProjectBillable').checked;

  if (!name) return showCreateProjectError('Name is required');
  if (!clientId) return showCreateProjectError('Pick a client');
  if (!rate || !Number.isFinite(Number(rate)) || Number(rate) < 0) return showCreateProjectError('Enter a rate');

  const btn = document.getElementById('submitNewProject');
  btn.disabled = true;
  document.getElementById('backToList').disabled = true;
  projectBusy = true;
  btn.textContent = 'Creating...';

  try {
    const res = await apiFetch('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify({
        name,
        clientId: parseInt(clientId),
        rateType,
        currentRate: parseFloat(rate),
        currency,
        billingFrequency,
        billable
      })
    });

    if (res.ok) {
      const project = await res.json();
      projects.unshift(project); // show at top (most recent)
      selectProject(project.id);
      // Back to list view
      document.getElementById('projectCreateView').style.display = 'none';
      document.getElementById('projectListView').style.display = 'block';
      document.getElementById('projectDropdown').classList.remove('create-mode');
      resetCreateProjectForm();
      // Refresh the picker list + project list in timer panel
      renderProjectDropdown('');
      renderProjectList();
      // Also refresh the expense project dropdown
      const expSelect = document.getElementById('expProject');
      const opt = document.createElement('option');
      opt.value = project.id;
      opt.textContent = project.clientName ? `${project.name} (${project.clientName})` : project.name;
      expSelect.appendChild(opt);
      // Close dropdown
      document.getElementById('projectDropdown').classList.remove('open');
      document.getElementById('projectPickerBtn').setAttribute('aria-expanded', 'false');
      document.getElementById('timerDesc').focus();
    } else {
      const err = await res.json().catch(() => ({}));
      showCreateProjectError(err.error || `Failed (${res.status})`);
    }
  } catch (err) {
    showCreateProjectError(err.message || String(err));
  }

  btn.disabled = false;
  projectBusy = false;
  document.getElementById('backToList').disabled = false;
  btn.textContent = 'Create project';
}

function showCreateProjectError(msg) {
  const fb = document.getElementById('createProjectFeedback');
  fb.textContent = msg;
  fb.className = 'feedback-inline error';
}

function resetCreateProjectForm() {
  document.getElementById('newProjectName').value = '';
  document.getElementById('newProjectClient').value = '';
  document.getElementById('newProjectRate').value = '';
  document.getElementById('newProjectRateType').value = 'hourly';
  document.getElementById('newProjectCurrency').value = 'USD';
  document.getElementById('newProjectBillable').checked = true;
  document.querySelectorAll('#projectCreateView select').forEach(select => select.dispatchEvent(new Event('ui-select:refresh')));
  updateFrequencyOptions('hourly');
  document.getElementById('createProjectFeedback').className = 'feedback-inline';
  document.getElementById('createProjectFeedback').textContent = '';
}

// --- Helpers ---

function pad(n) { return String(n).padStart(2, '0'); }

function escapeHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}
