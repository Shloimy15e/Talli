// --- API helper ---

async function apiFetch(path, options = {}) {
  const { serverUrl, apiToken } = await chrome.storage.local.get(['serverUrl', 'apiToken']);
  if (!serverUrl || !apiToken) {
    window.location.href = 'settings.html';
    throw new Error('Not configured');
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
    window.location.href = 'settings.html';
    throw new Error('Invalid API token');
  }
  return res;
}

// --- State ---

let projects = [];
let selectedProjectId = null;
let currentTimer = null;
let elapsedInterval = null;

// --- Init ---

document.addEventListener('DOMContentLoaded', async () => {
  try {
    await loadProjects();
    await loadTimer();
    document.getElementById('loading').style.display = 'none';
    document.getElementById('app').style.display = 'block';
  } catch {
    return;
  }

  setupModes();
  setupTimerBar();
  setupProjectPicker();
  setupExpenseForm();
  setupSettings();
  renderProjectList();
});

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

function setupSettings() {
  document.getElementById('settingsBtn').addEventListener('click', () => {
    window.location.href = 'settings.html';
  });
}

// --- Projects ---

async function loadProjects() {
  const res = await apiFetch('/api/v1/projects');
  projects = await res.json();

  // Expense project dropdown
  const expSelect = document.getElementById('expProject');
  projects.forEach(p => {
    const opt = document.createElement('option');
    opt.value = p.id;
    opt.textContent = p.clientName ? `${p.name} (${p.clientName})` : p.name;
    expSelect.appendChild(opt);
  });
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

async function loadTimer() {
  const res = await apiFetch('/api/v1/time/current');
  if (res.status === 204) {
    currentTimer = null;
    showTimerStopped();
  } else {
    currentTimer = await res.json();
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
}

function showTimerStopped() {
  if (elapsedInterval) {
    clearInterval(elapsedInterval);
    elapsedInterval = null;
  }
  document.getElementById('timerRunning').style.display = 'none';
  document.getElementById('timerStopped').style.display = 'flex';
  document.getElementById('timerBar').classList.remove('running');
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
  const start = new Date(currentTimer.startedAt);
  const now = new Date();
  const diff = Math.floor((now - start) / 1000);
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
  container.innerHTML = projects.map(p => `
    <div class="recent-entry" data-project-id="${p.id}">
      <div class="recent-entry-info">
        <div class="recent-entry-name">${escapeHtml(p.name)}</div>
        ${p.clientName ? `<div class="recent-entry-client">${escapeHtml(p.clientName)}</div>` : ''}
      </div>
      <button class="recent-entry-play" data-pid="${p.id}" title="Start timer">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,3 20,12 6,21"/></svg>
      </button>
    </div>
  `).join('');

  // Play button starts timer immediately
  container.querySelectorAll('.recent-entry-play').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      quickStart(parseInt(btn.dataset.pid));
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

// --- Helpers ---

function pad(n) { return String(n).padStart(2, '0'); }

function escapeHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}
