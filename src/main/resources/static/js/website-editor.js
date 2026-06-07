document.addEventListener('DOMContentLoaded', () => {
  const editor = document.querySelector('[data-website-editor]');
  if (!editor) return;

  const form = document.getElementById('website-editor-form');
  if (!form) return;

  const history = createEditorHistory(editor);

  setupSections(editor);
  editor.querySelectorAll('[data-repeat-list]').forEach(list => setupRepeatList(list, history));
  setupImagePickers(editor);
  setupKeyboardShortcuts(history);
  setupFormState(editor, form);
  refreshIcons();
});

function setupFormState(editor, form) {
  const saveState = editor.querySelector('[data-save-state]');
  const submitButton = editor.querySelector('[data-submit-button]');
  const submitLabel = editor.querySelector('[data-submit-label]');

  form.addEventListener('input', () => markDirty(editor));
  form.addEventListener('change', event => {
    if (event.target.matches('[data-section-select]')) return;
    markDirty(editor);
    if (event.target.matches('[data-preview-input]')) {
      showSelectedImage(event.target);
    }
  });

  form.addEventListener('submit', event => {
    const issues = collectPublishIssues(form);
    if (issues.length > 0) {
      event.preventDefault();
      showPublishChecks(editor, issues);
      return;
    }

    clearPublishChecks(editor);
    if (submitButton) {
      submitButton.disabled = true;
      submitButton.classList.add('opacity-75');
    }
    if (submitLabel) submitLabel.textContent = 'Publishing...';
    if (saveState) {
      saveState.textContent = 'Publishing';
      saveState.className = 'inline-flex items-center rounded-full bg-orange-100 px-2.5 py-1 text-xs font-semibold text-orange-700';
    }
  });
}

function markDirty(editor) {
  const saveState = editor.querySelector('[data-save-state]');
  if (!saveState || saveState.dataset.dirty === 'true') return;
  saveState.dataset.dirty = 'true';
  saveState.textContent = 'Unsaved changes';
  saveState.className = 'inline-flex items-center rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800';
}

function setupSections(editor) {
  const buttons = Array.from(editor.querySelectorAll('[data-section-target]'));
  const select = editor.querySelector('[data-section-select]');
  const sections = Array.from(editor.querySelectorAll('[data-editor-section]'));
  const activeClasses = ['border-[#ea7c28]', 'bg-orange-50', 'text-slate-950'];
  const inactiveClasses = ['border-transparent', 'text-slate-600'];

  function activate(sectionName) {
    const nextSection = sections.find(section => section.dataset.editorSection === sectionName) || sections[0];
    if (!nextSection) return;
    sections.forEach(section => {
      section.hidden = section !== nextSection;
    });
    buttons.forEach(button => {
      const active = button.dataset.sectionTarget === nextSection.dataset.editorSection;
      button.classList.toggle(activeClasses[0], active);
      button.classList.toggle(activeClasses[1], active);
      button.classList.toggle(activeClasses[2], active);
      button.classList.toggle(inactiveClasses[0], !active);
      button.classList.toggle(inactiveClasses[1], !active);
      button.setAttribute('aria-current', active ? 'page' : 'false');
    });
    if (select) select.value = nextSection.dataset.editorSection;
    if (window.history && window.location.hash !== `#${nextSection.id}`) {
      window.history.replaceState(null, '', `#${nextSection.id}`);
    }
  }

  buttons.forEach(button => {
    button.addEventListener('click', () => activate(button.dataset.sectionTarget));
  });
  if (select) {
    select.addEventListener('change', () => activate(select.value));
  }

  const initial = window.location.hash.replace('#section-', '') || sections[0]?.dataset.editorSection;
  activate(initial);
}

function setupRepeatList(list, history) {
  const rows = list.querySelector('[data-repeat-rows]');
  const template = list.querySelector('template[data-repeat-template]');
  if (!rows || !template) return;

  const addButton = list.querySelector('[data-add-row]');
  if (addButton) {
    addButton.addEventListener('click', () => {
      recordListChange(list, history, () => {
        rows.appendChild(template.content.firstElementChild.cloneNode(true));
      });
    });
  }

  list.addEventListener('click', event => {
    const moveRow = event.target.closest('[data-move-row]');
    if (moveRow && rows.contains(moveRow)) {
      const row = moveRow.closest('[data-repeat-row]');
      recordListChange(list, history, () => moveRepeatRow(list, row, moveRow.dataset.moveRow));
      return;
    }

    const removeRow = event.target.closest('[data-remove-row]');
    if (removeRow && rows.contains(removeRow)) {
      recordListChange(list, history, () => removeRow.closest('[data-repeat-row]').remove());
      return;
    }

    const addImageRow = event.target.closest('[data-add-image-row]');
    if (addImageRow && list.contains(addImageRow)) {
      recordListChange(list, history, () => {
        const imageList = addImageRow.closest('[data-image-list]');
        const imageRows = imageList.querySelector('[data-image-rows]');
        const imageTemplate = imageList.querySelector('template[data-image-template]');
        imageRows.appendChild(imageTemplate.content.firstElementChild.cloneNode(true));
      });
      return;
    }

    const removeImageRow = event.target.closest('[data-remove-image-row]');
    if (removeImageRow && list.contains(removeImageRow)) {
      recordListChange(list, history, () => removeImageRow.closest('[data-image-row]').remove());
    }
  });

  hydrateRepeatList(list);
}

function createEditorHistory(editor) {
  const undoStack = [];
  const redoStack = [];

  return {
    record(list, before, after) {
      if (before === after) return;
      undoStack.push({ list, before, after });
      redoStack.length = 0;
    },
    undo() {
      const entry = undoStack.pop();
      if (!entry) return;
      restoreRepeatRows(entry.list, entry.before);
      redoStack.push(entry);
      markDirty(editor);
    },
    redo() {
      const entry = redoStack.pop();
      if (!entry) return;
      restoreRepeatRows(entry.list, entry.after);
      undoStack.push(entry);
      markDirty(editor);
    },
  };
}

function setupKeyboardShortcuts(history) {
  document.addEventListener('keydown', event => {
    if (!(event.ctrlKey || event.metaKey)) return;
    if (isTextEditing(event.target)) return;

    const key = event.key.toLowerCase();
    if (key === 'z' && !event.shiftKey) {
      event.preventDefault();
      history.undo();
    }
    if (key === 'y' || (key === 'z' && event.shiftKey)) {
      event.preventDefault();
      history.redo();
    }
  });
}

function isTextEditing(target) {
  return target.matches('textarea, input[type="text"], input[type="email"], input[type="url"], input:not([type]), [contenteditable="true"]');
}

function recordListChange(list, history, callback) {
  const before = snapshotRepeatRows(list);
  callback();
  hydrateRepeatList(list);
  const after = snapshotRepeatRows(list);
  history.record(list, before, after);
  if (before !== after) {
    markListChanged(list);
    refreshIcons();
  }
}

function snapshotRepeatRows(list) {
  return list.querySelector('[data-repeat-rows]')?.innerHTML || '';
}

function restoreRepeatRows(list, html) {
  const rows = list.querySelector('[data-repeat-rows]');
  if (!rows) return;
  rows.innerHTML = html;
  hydrateRepeatList(list);
  markListChanged(list);
  refreshIcons();
}

function hydrateRepeatList(list) {
  resetImagePickers(list);
  renumberRepeatList(list);
  setupImagePickers(list);
}

function renumberRepeatList(list) {
  const rows = list.querySelector('[data-repeat-rows]');
  const itemLabel = list.dataset.itemLabel || 'Item';
  const directRows = rows.querySelectorAll(':scope > [data-repeat-row]');

  directRows.forEach((row, rowIndex) => {
    row.querySelectorAll('[data-repeat-field]').forEach(field => {
      field.name = `${field.dataset.repeatField}_${rowIndex}`;
    });
    const label = row.querySelector('[data-row-label]');
    if (label) label.textContent = `${itemLabel} ${rowIndex + 1}`;
    ensureRowControls(row, rowIndex, directRows.length);
    renumberImageRows(row, rowIndex);
  });

  const emptyState = list.querySelector('[data-empty-state]');
  if (emptyState) emptyState.classList.toggle('hidden', directRows.length > 0);
}

function ensureRowControls(row, rowIndex, totalRows) {
  let controls = row.querySelector('[data-row-controls]');
  if (!controls) {
    controls = document.createElement('div');
    controls.dataset.rowControls = 'true';
    controls.className = 'flex items-center gap-1';
    controls.innerHTML = `
      <button type="button" data-move-row="up" class="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40" title="Move up">
        <i data-lucide="arrow-up" class="h-3.5 w-3.5" aria-hidden="true"></i>
        <span>Up</span>
      </button>
      <button type="button" data-move-row="down" class="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40" title="Move down">
        <i data-lucide="arrow-down" class="h-3.5 w-3.5" aria-hidden="true"></i>
        <span>Down</span>
      </button>
    `;

    const removeButton = row.querySelector('[data-remove-row]');
    if (removeButton && removeButton.parentElement) {
      removeButton.parentElement.insertBefore(controls, removeButton);
    } else {
      row.prepend(controls);
    }
  }

  const moveUp = controls.querySelector('[data-move-row="up"]');
  const moveDown = controls.querySelector('[data-move-row="down"]');
  if (moveUp) moveUp.disabled = rowIndex === 0;
  if (moveDown) moveDown.disabled = rowIndex === totalRows - 1;
}

function moveRepeatRow(list, row, direction) {
  const rows = list.querySelector('[data-repeat-rows]');
  if (!row || row.parentElement !== rows) return;

  if (direction === 'up' && row.previousElementSibling) {
    rows.insertBefore(row, row.previousElementSibling);
  }
  if (direction === 'down' && row.nextElementSibling) {
    rows.insertBefore(row.nextElementSibling, row);
  }
}

function renumberImageRows(row, rowIndex) {
  const imageRows = row.querySelector('[data-image-rows]');
  if (!imageRows) return;

  imageRows.querySelectorAll(':scope > [data-image-row]').forEach((imageRow, imageIndex) => {
    imageRow.querySelectorAll('[data-image-field]').forEach(field => {
      field.name = `${field.dataset.imageField}_${rowIndex}_${imageIndex}`;
    });
  });
}

function setupImagePickers(scope) {
  scope.querySelectorAll('[data-preview-input]').forEach(input => {
    if (input.dataset.imagePickerReady === 'true') return;

    const imageScope = imageScopeFor(input);
    const hasCurrentImage = Boolean(imageScope?.querySelector('[data-preview-img]'));
    const controls = document.createElement('div');
    controls.dataset.imageControls = 'true';
    controls.className = 'mt-3 space-y-2';

    const row = document.createElement('div');
    row.className = 'flex flex-wrap items-center gap-2';

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'inline-flex h-9 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50';
    button.innerHTML = `
      <i data-lucide="image-plus" class="h-4 w-4" aria-hidden="true"></i>
      <span>${hasCurrentImage ? 'Replace image' : 'Choose image'}</span>
    `;
    button.addEventListener('click', () => input.click());

    const fileName = document.createElement('span');
    fileName.dataset.imageFileName = 'true';
    fileName.className = 'text-xs text-slate-500';
    fileName.textContent = 'No new image selected';

    const helper = document.createElement('p');
    helper.className = 'text-xs text-slate-400';
    helper.textContent = input.dataset.imageHelp || 'JPG, PNG, or WEBP.';

    row.appendChild(button);
    row.appendChild(fileName);
    controls.appendChild(row);
    controls.appendChild(helper);

    input.classList.add('sr-only');
    input.dataset.imagePickerReady = 'true';
    input.parentNode.insertBefore(controls, input);
    controls.appendChild(input);
  });
}

function resetImagePickers(scope) {
  scope.querySelectorAll('[data-image-controls]').forEach(controls => {
    const input = controls.querySelector('[data-preview-input]');
    if (input && controls.parentNode) {
      input.classList.remove('sr-only');
      delete input.dataset.imagePickerReady;
      controls.parentNode.insertBefore(input, controls);
    }
    controls.remove();
  });
}

function showSelectedImage(input) {
  const file = input.files && input.files[0];
  if (!file || !file.type.startsWith('image/')) return;

  const row = input.closest('[data-image-row], [data-repeat-row]');
  const scope = imageScopeFor(input);
  const shell = scope?.querySelector('[data-preview-shell]');
  if (!shell) return;

  let image = shell.querySelector('[data-preview-img]');
  const placeholder = shell.querySelector('[data-preview-placeholder]');
  if (!image) {
    image = document.createElement('img');
    image.dataset.previewImg = 'true';
    image.alt = '';
    image.className = row ? 'h-28 w-full object-cover' : 'h-40 w-full object-cover';
    shell.prepend(image);
  }
  image.src = URL.createObjectURL(file);
  if (placeholder) placeholder.hidden = true;

  const fileName = input.closest('[data-image-controls]')?.querySelector('[data-image-file-name]');
  if (fileName) {
    fileName.textContent = file.name;
    fileName.className = 'text-xs font-medium text-slate-700';
  }
}

function imageScopeFor(input) {
  return input.closest('[data-image-row], [data-repeat-row]')
      || input.closest('[data-image-controls]')?.parentElement
      || input.parentElement;
}

function collectPublishIssues(form) {
  const issues = [];

  form.querySelectorAll('[data-publish-required]').forEach(field => {
    if (!fieldValue(field)) {
      issues.push(field.dataset.publishRequired);
    }
  });

  form.querySelectorAll('[data-publish-email]').forEach(field => {
    const value = fieldValue(field);
    if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      issues.push(field.dataset.publishEmail);
    }
  });

  form.querySelectorAll('[data-repeat-list]').forEach(list => {
    const rows = list.querySelectorAll('[data-repeat-rows] > [data-repeat-row]');
    rows.forEach((row, index) => {
      if (!rowHasContent(row)) return;

      row.querySelectorAll('[data-repeat-required]').forEach(field => {
        if (!fieldValue(field)) {
          issues.push(field.dataset.repeatRequired.replace('{number}', index + 1));
        }
      });
    });
  });

  return [...new Set(issues.filter(Boolean))];
}

function fieldValue(field) {
  if (field.type === 'file') return field.files && field.files.length > 0 ? field.files[0].name : '';
  return (field.value || '').trim();
}

function rowHasContent(row) {
  return Array.from(row.querySelectorAll('input, textarea')).some(field => {
    if (field.type === 'hidden') return false;
    return Boolean(fieldValue(field));
  });
}

function showPublishChecks(editor, issues) {
  const panel = editor.querySelector('[data-publish-checks]');
  const list = editor.querySelector('[data-publish-check-list]');
  if (!panel || !list) return;

  list.innerHTML = '';
  issues.forEach(issue => {
    const item = document.createElement('li');
    item.textContent = issue;
    list.appendChild(item);
  });
  panel.classList.remove('hidden');
  panel.scrollIntoView({ behavior: 'smooth', block: 'center' });
  refreshIcons();
}

function clearPublishChecks(editor) {
  const panel = editor.querySelector('[data-publish-checks]');
  if (panel) panel.classList.add('hidden');
}

function markListChanged(list) {
  list.dispatchEvent(new Event('input', { bubbles: true }));
}

function refreshIcons() {
  if (window.lucide) window.lucide.createIcons();
}
