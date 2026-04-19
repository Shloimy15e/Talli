// --- API helper ---

async function apiFetch(path, options = {}) {
  const { serverUrl, apiToken } = await chrome.storage.local.get(['serverUrl', 'apiToken']);
  if (!serverUrl || !apiToken) {
    throw new Error('Not configured — set server URL and API token in Settings.');
  }
  const res = await fetch(`${serverUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiToken}`,
      ...(options.headers || {})
    }
  });
  if (res.status === 401) {
    throw new Error('Invalid API token.');
  }
  return res;
}

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

  initApp();
});

async function initApp() {
  try {
    await loadProjects();
    await loadTimer();
    document.getElementById('loading').style.display = 'none';
    document.getElementById('errorState').style.display = 'none';
    document.getElementById('app').style.display = 'block';
  } catch (err) {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('app').style.display = 'none';
    document.getElementById('errorMessage').textContent = String(err.message || err);
    document.getElementById('errorState').style.display = 'flex';
    return;
  }

  setupModes();
  setupTimerBar();
  setupProjectPicker();
  setupExpenseForm();
  setupCreateProject();
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

async function loadProjects() {
  const res = await apiFetch('/api/v1/projects');
  projects = await res.json();

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
    }
  });

  // Close on outside click
  document.addEventListener('click', (e) => {
    if (!document.getElementById('projectPicker').contains(e.target)) {
      dropdown.classList.remove('open');
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
    <div class="project-option ${p.id === selectedProjectId ? 'selected' : ''}" data-id="${p.id}">
      <span class="project-option-name">${escapeHtml(p.name)}</span>
      ${p.clientName ? `<span class="project-option-client">${escapeHtml(p.clientName)}</span>` : ''}
    </div>
  `).join('');

  if (filtered.length === 0) {
    list.innerHTML = '<div style="padding:10px;text-align:center;color:#94a3b8;font-size:12px">No matches</div>';
  }

  list.querySelectorAll('.project-option').forEach(opt => {
    opt.addEventListener('click', () => {
      selectProject(parseInt(opt.dataset.id));
      document.getElementById('projectDropdown').classList.remove('open');
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
    label.textContent = 'Project';
    btn.classList.remove('has-value');
  }
}

// --- Timer ---

// Baseline for elapsed-time display — captures the server-computed elapsed
// at fetch time and the client timestamp at that moment. Eliminates any
// dependency on server/client clock sync or timezone alignment.
let elapsedBaselineSeconds = 0;
let elapsedBaselineClientMs = 0;

async function loadTimer() {
  const res = await apiFetch('/api/v1/time/current');
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
  document.getElementById('timerRunning').style.display = 'flex';
  document.getElementById('timerBar').classList.add('running');

  document.getElementById('runningDesc').textContent = currentTimer.description || 'No description';
  document.getElementById('runningProject').textContent = currentTimer.projectName || '';

  updateElapsed();
  elapsedInterval = setInterval(updateElapsed, 1000);
  renderProjectList();
}

function showTimerStopped() {
  if (elapsedInterval) {
    clearInterval(elapsedInterval);
    elapsedInterval = null;
  }
  document.getElementById('timerRunning').style.display = 'none';
  document.getElementById('timerStopped').style.display = 'flex';
  document.getElementById('timerBar').classList.remove('running');
  renderProjectList();
}

function setupTimerBar() {
  document.getElementById('startBtn').addEventListener('click', startTimer);
  document.getElementById('timerDesc').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') startTimer();
  });
  document.getElementById('stopBtn').addEventListener('click', stopTimer);
}

function updateElapsed() {
  if (!currentTimer) return;
  // Server-authoritative baseline + local increment. No clock sync required.
  const localDelta = Math.floor((Date.now() - elapsedBaselineClientMs) / 1000);
  const diff = elapsedBaselineSeconds + localDelta;
  const h = Math.floor(diff / 3600);
  const m = Math.floor((diff % 3600) / 60);
  const s = diff % 60;
  document.getElementById('elapsed').textContent = `${pad(h)}:${pad(m)}:${pad(s)}`;
}

async function startTimer() {
  if (!selectedProjectId) {
    // Open the project picker
    document.getElementById('projectPickerBtn').click();
    return;
  }

  const description = document.getElementById('timerDesc').value.trim();
  const btn = document.getElementById('startBtn');
  btn.disabled = true;

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
  } catch { /* handled */ }

  btn.disabled = false;
}

async function stopTimer() {
  if (!currentTimer) return;
  const btn = document.getElementById('stopBtn');
  btn.disabled = true;

  try {
    const res = await apiFetch(`/api/v1/time/${currentTimer.id}/stop`, { method: 'POST' });
    if (res.ok) {
      currentTimer = null;
      showTimerStopped();
      chrome.runtime.sendMessage({ type: 'timerStopped' });
    }
  } catch { /* handled */ }

  btn.disabled = false;
}

async function quickStart(projectId) {
  try {
    const res = await apiFetch('/api/v1/time/start', {
      method: 'POST',
      body: JSON.stringify({ projectId, description: null })
    });
    if (res.ok) {
      currentTimer = await res.json();
      elapsedBaselineSeconds = 0;
      elapsedBaselineClientMs = Date.now();
      showTimerRunning();
      chrome.runtime.sendMessage({ type: 'timerStarted' });
    }
  } catch { /* handled */ }
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

  container.innerHTML = projects.map(p => {
    const isRunning = p.id === runningProjectId;
    return `
    <div class="recent-entry" data-project-id="${p.id}">
      <div class="recent-entry-info">
        <div class="recent-entry-name">${escapeHtml(p.name)}</div>
        ${p.clientName ? `<div class="recent-entry-client">${escapeHtml(p.clientName)}</div>` : ''}
      </div>
      <button class="recent-entry-play${isRunning ? ' running' : ''}" data-pid="${p.id}" data-running="${isRunning}" title="${isRunning ? 'Stop timer' : 'Start timer'}">
        ${isRunning ? stopIcon : playIcon}
      </button>
    </div>
    `;
  }).join('');

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

  // Clicking row selects project in the timer bar
  container.querySelectorAll('.recent-entry').forEach(row => {
    row.addEventListener('click', () => {
      selectProject(parseInt(row.dataset.projectId));
      document.getElementById('timerDesc').focus();
    });
  });
}

// --- Expense ---

function setupExpenseForm() {
  document.getElementById('expenseForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const feedback = document.getElementById('expenseFeedback');
    const btn = e.target.querySelector('button[type="submit"]');

    const projectId = document.getElementById('expProject').value;
    const amount = document.getElementById('expAmount').value;
    const category = document.getElementById('expCategory').value;
    const vendor = document.getElementById('expVendor').value;
    const description = document.getElementById('expDescription').value;

    btn.disabled = true;
    btn.textContent = 'Saving...';

    try {
      const res = await apiFetch('/api/v1/expenses', {
        method: 'POST',
        body: JSON.stringify({
          projectId: projectId ? parseInt(projectId) : null,
          amount: parseFloat(amount),
          category,
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
    } catch {
      feedback.textContent = 'Cannot reach server';
      feedback.className = 'feedback error';
    }

    btn.disabled = false;
    btn.textContent = 'Save Expense';
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
  const name = document.getElementById('newProjectName').value.trim();
  const clientId = document.getElementById('newProjectClient').value;
  const rate = document.getElementById('newProjectRate').value;
  const rateType = document.getElementById('newProjectRateType').value;
  const currency = document.getElementById('newProjectCurrency').value;
  const billingFrequency = document.getElementById('newProjectFrequency').value;
  const billable = document.getElementById('newProjectBillable').checked;

  if (!name) return showCreateProjectError('Name is required');
  if (!clientId) return showCreateProjectError('Pick a client');
  if (!rate || parseFloat(rate) <= 0) return showCreateProjectError('Enter a rate');

  const btn = document.getElementById('submitNewProject');
  btn.disabled = true;
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
      document.getElementById('timerDesc').focus();
    } else {
      const err = await res.json().catch(() => ({}));
      showCreateProjectError(err.error || `Failed (${res.status})`);
    }
  } catch (err) {
    showCreateProjectError(err.message || String(err));
  }

  btn.disabled = false;
  btn.textContent = 'Create';
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
