document.addEventListener('DOMContentLoaded', () => {
  const editor = document.querySelector('[data-website-editor]');
  if (!editor) return;

  const form = document.getElementById('website-editor-form');
  if (!form) return;

  const history = createEditorHistory(editor, form);

  setupSections(editor);
  setupHistoryControls(editor, history);
  setupFieldHistory(form, history);
  editor.querySelectorAll('[data-repeat-list]').forEach(list => setupRepeatList(editor, list, history));
  setupImagePickers(editor, history);
  setupKeyboardShortcuts(history);
  setupFormState(editor, form, history);
  updateSectionHealth(editor, form);
  refreshIcons();
});

function setupFormState(editor, form, history) {
  const saveState = editor.querySelector('[data-save-state]');
  const submitButton = editor.querySelector('[data-submit-button]');
  const submitLabel = editor.querySelector('[data-submit-label]');
  const confirmPublish = editor.querySelector('[data-confirm-publish]');
  const cancelPublish = editor.querySelector('[data-cancel-publish]');

  form.addEventListener('input', event => {
    if (isSectionSelect(event.target)) return;
    markDirty(editor, form, event.target);
    updateSectionHealth(editor, form);
    clearPublishChecks(editor);
  });

  form.addEventListener('change', event => {
    if (isSectionSelect(event.target)) return;
    markDirty(editor, form, event.target);
    if (isElement(event.target) && event.target.matches('[data-preview-input]')) {
      showSelectedImage(event.target, history);
    }
    updateSectionHealth(editor, form);
    clearPublishChecks(editor);
  });

  form.addEventListener('submit', event => {
    const issues = collectPublishIssues(form);
    if (issues.length > 0) {
      event.preventDefault();
      form.dataset.publishConfirmed = 'false';
      showPublishChecks(editor, issues);
      return;
    }

    clearPublishChecks(editor);
    if (form.dataset.publishConfirmed !== 'true') {
      event.preventDefault();
      showPublishReview(editor);
      return;
    }

    form.dataset.submitting = 'true';
    if (submitButton) {
      submitButton.disabled = true;
      submitButton.classList.add('opacity-75');
    }
    if (submitLabel) submitLabel.textContent = 'Publishing...';
    if (saveState) {
      saveState.textContent = 'Publishing...';
      saveState.className = 'inline-flex items-center rounded-full bg-orange-100 px-2.5 py-1 text-xs font-semibold text-orange-700';
    }
  });

  if (confirmPublish) {
    confirmPublish.addEventListener('click', () => {
      hidePublishReview(editor);
      form.dataset.publishConfirmed = 'true';
      form.requestSubmit(submitButton || undefined);
    });
  }

  if (cancelPublish) {
    cancelPublish.addEventListener('click', () => {
      hidePublishReview(editor);
      form.dataset.publishConfirmed = 'false';
    });
  }

  window.addEventListener('beforeunload', event => {
    if (editor.dataset.dirty !== 'true' || form.dataset.submitting === 'true') return;
    event.preventDefault();
    event.returnValue = '';
  });
}

function markDirty(editor, form, source) {
  if (form) form.dataset.publishConfirmed = 'false';

  const section = closestSection(source);
  if (section) section.dataset.changed = 'true';

  const saveState = editor.querySelector('[data-save-state]');
  if (!saveState || editor.dataset.dirty === 'true') return;

  editor.dataset.dirty = 'true';
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

function setupHistoryControls(editor, history) {
  const undoButton = editor.querySelector('[data-undo-button]');
  const redoButton = editor.querySelector('[data-redo-button]');
  const toastUndo = editor.querySelector('[data-toast-undo]');

  if (undoButton) {
    undoButton.addEventListener('click', () => history.undo());
  }
  if (redoButton) {
    redoButton.addEventListener('click', () => history.redo());
  }
  if (toastUndo) {
    toastUndo.addEventListener('click', () => {
      history.undo();
      hideUndoToast(editor);
    });
  }

  history.onChange(() => {
    if (undoButton) undoButton.disabled = !history.canUndo();
    if (redoButton) redoButton.disabled = !history.canRedo();
  });
}

function setupFieldHistory(form, history) {
  form.addEventListener('focusin', event => {
    if (!isHistoryField(event.target)) return;
    event.target.dataset.historyValue = event.target.value || '';
  });

  form.addEventListener('change', event => {
    if (!isHistoryField(event.target)) return;
    const field = event.target;
    const before = field.dataset.historyValue ?? '';
    const after = field.value || '';
    if (before === after) return;

    history.record({
      source: field,
      undo() {
        field.value = before;
        field.dispatchEvent(new Event('input', { bubbles: true }));
      },
      redo() {
        field.value = after;
        field.dispatchEvent(new Event('input', { bubbles: true }));
      },
    });
    field.dataset.historyValue = after;
  });
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

function setupRepeatList(editor, list, history) {
  const rows = list.querySelector('[data-repeat-rows]');
  const template = list.querySelector('template[data-repeat-template]');
  if (!rows || !template) return;

  const addButton = list.querySelector('[data-add-row]');
  if (addButton) {
    addButton.addEventListener('click', () => {
      let newRow = null;
      recordListChange(list, history, () => {
        newRow = template.content.firstElementChild.cloneNode(true);
        rows.appendChild(newRow);
      });
      if (newRow) {
        setRepeatRowExpanded(newRow, true);
        firstEditableField(newRow)?.focus();
      }
    });
  }

  list.addEventListener('input', event => {
    const row = isElement(event.target) ? event.target.closest('[data-repeat-row]') : null;
    if (row && list.contains(row)) updateRowSummary(row);
  });

  list.addEventListener('change', event => {
    const row = isElement(event.target) ? event.target.closest('[data-repeat-row]') : null;
    if (row && list.contains(row)) updateRowSummary(row);
  });

  list.addEventListener('click', event => {
    const toggle = event.target.closest('[data-toggle-row]');
    if (toggle && rows.contains(toggle)) {
      const row = toggle.closest('[data-repeat-row]');
      setRepeatRowExpanded(row, toggle.getAttribute('aria-expanded') !== 'true');
      return;
    }

    const moveRow = event.target.closest('[data-move-row]');
    if (moveRow && rows.contains(moveRow)) {
      const row = moveRow.closest('[data-repeat-row]');
      recordListChange(list, history, () => moveRepeatRow(list, row, moveRow.dataset.moveRow));
      return;
    }

    const removeRow = event.target.closest('[data-remove-row]');
    if (removeRow && rows.contains(removeRow)) {
      const row = removeRow.closest('[data-repeat-row]');
      const message = removedRowMessage(list, row);
      recordListChange(list, history, () => row.remove());
      showUndoToast(editor, message);
      return;
    }

    const addImageRow = event.target.closest('[data-add-image-row]');
    if (addImageRow && list.contains(addImageRow)) {
      let imageRow = null;
      recordListChange(list, history, () => {
        const imageList = addImageRow.closest('[data-image-list]');
        const imageRows = imageList.querySelector('[data-image-rows]');
        const imageTemplate = imageList.querySelector('template[data-image-template]');
        imageRow = imageTemplate.content.firstElementChild.cloneNode(true);
        imageRows.appendChild(imageRow);
      });
      if (imageRow) {
        const parentRow = imageRow.closest('[data-repeat-row]');
        if (parentRow) setRepeatRowExpanded(parentRow, true);
      }
      return;
    }

    const removeImageRow = event.target.closest('[data-remove-image-row]');
    if (removeImageRow && list.contains(removeImageRow)) {
      const imageRow = removeImageRow.closest('[data-image-row]');
      const message = removedImageMessage(imageRow);
      recordListChange(list, history, () => imageRow.remove());
      showUndoToast(editor, message);
    }
  });

  hydrateRepeatList(list, history);
  collapseInitialRepeatRows(list);
}

function createEditorHistory(editor, form) {
  const undoStack = [];
  const redoStack = [];
  const listeners = [];

  function notify() {
    listeners.forEach(listener => listener());
  }

  function afterChange(entry) {
    markDirty(editor, form, entry.source);
    updateSectionHealth(editor, form);
    refreshIcons();
    notify();
  }

  return {
    record(entry) {
      undoStack.push(entry);
      redoStack.length = 0;
      notify();
    },
    undo() {
      const entry = undoStack.pop();
      if (!entry) return;
      entry.undo();
      if (typeof entry.redo === 'function') {
        redoStack.push(entry);
      }
      afterChange(entry);
    },
    redo() {
      const entry = redoStack.pop();
      if (!entry) return;
      entry.redo();
      undoStack.push(entry);
      afterChange(entry);
    },
    canUndo() {
      return undoStack.length > 0;
    },
    canRedo() {
      return redoStack.length > 0;
    },
    onChange(listener) {
      listeners.push(listener);
      listener();
    },
  };
}

function recordListChange(list, history, callback) {
  const before = snapshotRepeatRows(list);
  callback();
  hydrateRepeatList(list, history);
  const after = snapshotRepeatRows(list);
  if (before !== after) {
    history.record({
      source: list,
      undo() {
        restoreRepeatRows(list, before, history);
      },
      redo() {
        restoreRepeatRows(list, after, history);
      },
    });
    markListChanged(list);
    refreshIcons();
  }
}

function snapshotRepeatRows(list) {
  return list.querySelector('[data-repeat-rows]')?.innerHTML || '';
}

function restoreRepeatRows(list, html, history) {
  const rows = list.querySelector('[data-repeat-rows]');
  if (!rows) return;
  rows.innerHTML = html;
  hydrateRepeatList(list, history);
  markListChanged(list);
}

function hydrateRepeatList(list, history) {
  resetImagePickers(list);
  renumberRepeatList(list);
  setupImagePickers(list, history);
}

function renumberRepeatList(list) {
  const rows = list.querySelector('[data-repeat-rows]');
  if (!rows) return;

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
    updateRowSummary(row);
  });

  const emptyState = list.querySelector('[data-empty-state]');
  if (emptyState) emptyState.classList.toggle('hidden', directRows.length > 0);
}

function ensureRowControls(row, rowIndex, totalRows) {
  let controls = row.querySelector('[data-row-controls]');
  if (!controls) {
    controls = document.createElement('div');
    controls.dataset.rowControls = 'true';
    controls.className = 'hidden items-center gap-1 sm:flex';
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

function collapseInitialRepeatRows(list) {
  if (list.dataset.collapseReady === 'true') return;
  list.dataset.collapseReady = 'true';

  const rows = list.querySelectorAll('[data-repeat-rows] > [data-repeat-row]');
  rows.forEach(row => setRepeatRowExpanded(row, rows.length <= 1));
}

function setRepeatRowExpanded(row, expanded) {
  if (!row) return;
  const body = row.querySelector('[data-row-body]');
  const toggle = row.querySelector('[data-toggle-row]');
  const chevron = row.querySelector('[data-row-chevron]');
  if (body) body.hidden = !expanded;
  if (toggle) toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
  if (chevron) chevron.style.transform = expanded ? 'rotate(0deg)' : 'rotate(-90deg)';
}

function updateRowSummary(row) {
  const summary = row.querySelector('[data-row-summary]');
  if (!summary) return;

  const textFields = Array.from(row.querySelectorAll('[data-repeat-field]'))
      .filter(field => field.type !== 'hidden' && field.type !== 'file')
      .map(field => ({
        field,
        value: fieldValue(field),
        label: repeatFieldLabel(field),
      }))
      .filter(item => item.value.length > 0);

  const number = textFields.find(item => isSummaryField(item, 'number'));
  const title = textFields.find(item => isSummaryField(item, 'title') || isSummaryField(item, 'name') || isSummaryField(item, 'location'));
  if (number && title && number !== title) {
    summary.textContent = `${number.value} - ${title.value}`;
    return;
  }

  if (title) {
    summary.textContent = title.value;
    return;
  }

  if (textFields.length > 0) {
    summary.textContent = textFields[0].value;
    return;
  }

  const imageCount = row.querySelectorAll('[data-image-row] [data-preview-img]').length;
  if (imageCount > 0) {
    summary.textContent = `${imageCount} image${imageCount === 1 ? '' : 's'}`;
    return;
  }

  summary.textContent = 'Not filled in yet';
}

function removedRowMessage(list, row) {
  if (!row) return `Removed ${list.dataset.itemLabel || 'item'}.`;

  updateRowSummary(row);
  const label = rowLabel(row) || list.dataset.itemLabel || 'Item';
  const summary = rowSummary(row);
  return summary ? `Removed ${label}: ${summary}.` : `Removed ${label}.`;
}

function removedImageMessage(imageRow) {
  if (!imageRow) return 'Removed image.';

  const parentRow = imageRow.closest('[data-repeat-row]');
  const imageNumber = imageRowNumber(imageRow);
  const imageLabel = imageNumber ? `image ${imageNumber}` : 'image';

  if (!parentRow) return `Removed ${imageLabel}.`;

  updateRowSummary(parentRow);
  const label = rowLabel(parentRow);
  const summary = rowSummary(parentRow);
  if (label && summary) return `Removed ${imageLabel} from ${label}: ${summary}.`;
  if (label) return `Removed ${imageLabel} from ${label}.`;
  return `Removed ${imageLabel}.`;
}

function rowLabel(row) {
  return cleanMessagePart(row.querySelector('[data-row-label]')?.textContent);
}

function rowSummary(row) {
  const value = cleanMessagePart(row.querySelector('[data-row-summary]')?.textContent);
  return value === 'Not filled in yet' ? '' : value;
}

function imageRowNumber(imageRow) {
  const rows = imageRow.parentElement
      ? Array.from(imageRow.parentElement.querySelectorAll(':scope > [data-image-row]'))
      : [];
  const index = rows.indexOf(imageRow);
  return index >= 0 ? index + 1 : null;
}

function cleanMessagePart(value) {
  return (value || '').replace(/\s+/g, ' ').trim();
}

function repeatFieldLabel(field) {
  return field.closest('label, div')?.querySelector('span, p')?.textContent?.trim().toLowerCase() || '';
}

function isSummaryField(item, name) {
  const fieldName = item.field.dataset.repeatField?.toLowerCase() || '';
  return item.label === name || fieldName.endsWith(name);
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

function setupImagePickers(scope, history) {
  scope.querySelectorAll('[data-preview-input]').forEach(input => {
    if (input.dataset.imagePickerReady === 'true') return;

    const imageScope = imageScopeFor(input);
    const currentImage = imageScope?.querySelector('[data-preview-img]');
    const hasCurrentImage = Boolean(currentImage);
    input.dataset.originalHadImage = hasCurrentImage ? 'true' : 'false';
    input.dataset.originalSrc = currentImage?.getAttribute('src') || '';

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
      <span>${hasCurrentImage ? 'Choose new image' : 'Choose image'}</span>
    `;
    button.addEventListener('click', () => input.click());

    const fileName = document.createElement('span');
    fileName.dataset.imageFileName = 'true';
    fileName.className = 'text-xs text-slate-500';
    fileName.textContent = 'No new image selected';

    const clearButton = document.createElement('button');
    clearButton.type = 'button';
    clearButton.dataset.clearImageChoice = 'true';
    clearButton.className = 'hidden text-xs font-semibold text-[#ea7c28] hover:text-[#c7611b]';
    clearButton.textContent = 'Keep current image';
    clearButton.addEventListener('click', () => clearSelectedImage(input));

    const helper = document.createElement('p');
    helper.className = 'text-xs text-slate-400';
    helper.textContent = input.dataset.imageHelp || 'Recommended: clear JPG, PNG, or WEBP image.';

    row.appendChild(button);
    row.appendChild(fileName);
    row.appendChild(clearButton);
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

function showSelectedImage(input, history) {
  const file = input.files && input.files[0];
  if (!file || !file.type.startsWith('image/')) return;

  const row = input.closest('[data-image-row], [data-repeat-row]');
  const scope = imageScopeFor(input);
  const shell = scope?.querySelector('[data-preview-shell]');
  if (!shell) return;

  if (history) {
    history.record({
      source: input,
      undo() {
        clearSelectedImage(input);
        input.dispatchEvent(new Event('input', { bubbles: true }));
      },
    });
  }

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

  const controls = input.closest('[data-image-controls]');
  const fileName = controls?.querySelector('[data-image-file-name]');
  if (fileName) {
    fileName.textContent = `New image: ${file.name}`;
    fileName.className = 'text-xs font-medium text-slate-700';
  }
  const clearButton = controls?.querySelector('[data-clear-image-choice]');
  if (clearButton) clearButton.classList.remove('hidden');

  const repeatRow = input.closest('[data-repeat-row]');
  if (repeatRow) updateRowSummary(repeatRow);
}

function clearSelectedImage(input) {
  input.value = '';

  const scope = imageScopeFor(input);
  const shell = scope?.querySelector('[data-preview-shell]');
  const placeholder = shell?.querySelector('[data-preview-placeholder]');
  let image = shell?.querySelector('[data-preview-img]');

  if (input.dataset.originalHadImage === 'true') {
    if (!image && shell) {
      image = document.createElement('img');
      image.dataset.previewImg = 'true';
      image.alt = '';
      image.className = input.closest('[data-repeat-row], [data-image-row]') ? 'h-28 w-full object-cover' : 'h-40 w-full object-cover';
      shell.prepend(image);
    }
    if (image) image.src = input.dataset.originalSrc || '';
    if (placeholder) placeholder.hidden = true;
  } else {
    if (image) image.remove();
    if (placeholder) placeholder.hidden = false;
  }

  const controls = input.closest('[data-image-controls]');
  const fileName = controls?.querySelector('[data-image-file-name]');
  if (fileName) {
    fileName.textContent = 'No new image selected';
    fileName.className = 'text-xs text-slate-500';
  }
  const clearButton = controls?.querySelector('[data-clear-image-choice]');
  if (clearButton) clearButton.classList.add('hidden');

  const repeatRow = input.closest('[data-repeat-row]');
  if (repeatRow) updateRowSummary(repeatRow);
}

function imageScopeFor(input) {
  return input.closest('[data-image-row], [data-repeat-row]')
      || input.closest('[data-image-controls]')?.parentElement
      || input.parentElement;
}

function collectPublishIssues(scope) {
  const issues = [];

  scope.querySelectorAll('[data-publish-required]').forEach(field => {
    if (!fieldValue(field)) {
      issues.push(field.dataset.publishRequired);
    }
  });

  scope.querySelectorAll('[data-publish-email]').forEach(field => {
    const value = fieldValue(field);
    if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      issues.push(field.dataset.publishEmail);
    }
  });

  scope.querySelectorAll('[data-repeat-list]').forEach(list => {
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

function updateSectionHealth(editor, form) {
  const sections = Array.from(editor.querySelectorAll('[data-editor-section]'));
  let sectionsWithIssues = 0;

  sections.forEach(section => {
    const issues = collectPublishIssues(section);
    const hasIssues = issues.length > 0;
    if (hasIssues) sectionsWithIssues++;

    const button = editor.querySelector(`[data-section-target="${section.dataset.editorSection}"]`);
    const status = button?.querySelector('[data-section-health]');
    if (status) {
      status.textContent = hasIssues ? 'Needs review' : '';
      status.className = hasIssues
          ? 'ml-auto shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-800'
          : 'hidden ml-auto shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-800';
    }
    if (button) {
      if (hasIssues) {
        button.title = `${section.dataset.sectionLabel || 'Section'} needs attention`;
      } else {
        button.removeAttribute('title');
      }
    }
  });

  const summary = editor.querySelector('[data-section-health-summary]');
  if (summary) {
    summary.textContent = sectionsWithIssues === 0
        ? ''
        : `${sectionsWithIssues} section${sectionsWithIssues === 1 ? '' : 's'} need attention.`;
    summary.hidden = sectionsWithIssues === 0;
  }
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

function showPublishReview(editor) {
  const review = editor.querySelector('[data-publish-review]');
  const intro = editor.querySelector('[data-publish-review-intro]');
  const list = editor.querySelector('[data-publish-review-list]');
  if (!review || !intro || !list) return;

  const changedSections = Array.from(editor.querySelectorAll('[data-editor-section][data-changed="true"]'))
      .map(section => section.dataset.sectionLabel || section.dataset.editorSection);

  list.innerHTML = '';
  if (changedSections.length === 0) {
    intro.textContent = 'No edited sections were detected. You can still publish to refresh the live website.';
    list.appendChild(reviewItem('No changed sections detected'));
  } else {
    intro.textContent = 'These sections have changes ready to publish.';
    changedSections.forEach(label => list.appendChild(reviewItem(label)));
  }

  review.classList.remove('hidden');
  review.classList.add('flex');
  refreshIcons();
}

function hidePublishReview(editor) {
  const review = editor.querySelector('[data-publish-review]');
  if (!review) return;
  review.classList.add('hidden');
  review.classList.remove('flex');
}

function reviewItem(text) {
  const item = document.createElement('li');
  item.className = 'flex items-center gap-2 rounded-md bg-slate-50 px-3 py-2';
  item.innerHTML = '<i data-lucide="check-circle-2" class="h-4 w-4 text-emerald-600" aria-hidden="true"></i>';
  const label = document.createElement('span');
  label.textContent = text;
  item.appendChild(label);
  return item;
}

function showUndoToast(editor, message) {
  const toast = editor.querySelector('[data-editor-toast]');
  const label = editor.querySelector('[data-toast-message]');
  if (!toast || !label) return;

  label.textContent = message;
  toast.classList.remove('hidden');
  window.clearTimeout(toast.hideTimer);
  toast.hideTimer = window.setTimeout(() => hideUndoToast(editor), 6000);
}

function hideUndoToast(editor) {
  const toast = editor.querySelector('[data-editor-toast]');
  if (!toast) return;
  toast.classList.add('hidden');
  window.clearTimeout(toast.hideTimer);
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

function firstEditableField(row) {
  return row.querySelector('input:not([type="hidden"]):not([type="file"]), textarea');
}

function markListChanged(list) {
  list.dispatchEvent(new Event('input', { bubbles: true }));
}

function closestSection(source) {
  return isElement(source) ? source.closest('[data-editor-section]') : null;
}

function isSectionSelect(target) {
  return isElement(target) && target.matches('[data-section-select]');
}

function isHistoryField(target) {
  return isElement(target)
      && target.matches('textarea, input[type="text"], input[type="email"], input[type="url"], input:not([type])')
      && !target.matches('[data-section-select]');
}

function isTextEditing(target) {
  return isElement(target)
      && target.matches('textarea, input[type="text"], input[type="email"], input[type="url"], input:not([type]), [contenteditable="true"]');
}

function isElement(value) {
  return value && value.nodeType === Node.ELEMENT_NODE;
}

function refreshIcons() {
  if (window.lucide) window.lucide.createIcons();
}
