(function () {
  'use strict';

  const instances = new Set();
  let openSelect = null;
  let nextId = 0;

  function splitLabel(label) {
    const match = label.match(/^(.+?)\s*<([^>]+)>$/);
    return match ? { primary: match[1].trim(), secondary: match[2].trim() } : { primary: label, secondary: '' };
  }

  function optionRecords(select) {
    return Array.from(select.options).map((option, index) => ({
      ...splitLabel(option.label || option.textContent.trim()),
      disabled: option.disabled || Boolean(option.parentElement?.disabled),
      index,
      label: option.label || option.textContent.trim(),
      selected: option.selected
    }));
  }

  function edgeEnabledIndex(options, fromEnd) {
    const enabled = options.filter((option) => !option.disabled);
    return enabled.length ? enabled[fromEnd ? enabled.length - 1 : 0].index : -1;
  }

  function nextEnabledIndex(options, current, direction) {
    const enabled = options.filter((option) => !option.disabled);
    if (!enabled.length) return -1;
    const position = enabled.findIndex((option) => option.index === current);
    if (position < 0) return enabled[direction < 0 ? enabled.length - 1 : 0].index;
    return enabled[Math.min(enabled.length - 1, Math.max(0, position + direction))].index;
  }

  function typeaheadIndex(options, query, current) {
    const typed = query.trim().toLocaleLowerCase();
    const needle = typed && Array.from(typed).every((character) => character === typed[0]) ? typed[0] : typed;
    if (!needle) return current;
    const currentPosition = options.findIndex((option) => option.index === current);
    for (let offset = 1; offset <= options.length; offset += 1) {
      const option = options[(Math.max(currentPosition, -1) + offset) % options.length];
      const searchable = [option.primary, option.secondary, option.label]
        .filter(Boolean)
        .map((value) => value.toLocaleLowerCase());
      if (!option.disabled && searchable.some((value) => value.startsWith(needle))) return option.index;
    }
    return current;
  }

  function filterOptions(options, query) {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return options;
    return options.filter((option) => [option.primary, option.secondary, option.label]
      .filter(Boolean)
      .some((value) => value.toLocaleLowerCase().includes(needle)));
  }

  function popupPlacement(rect, viewportWidth, viewportHeight, preferredHeight) {
    const margin = 8;
    const gap = 4;
    const width = Math.min(Math.max(rect.width, 180), viewportWidth - margin * 2);
    const left = Math.min(Math.max(rect.left, margin), viewportWidth - width - margin);
    const below = viewportHeight - rect.bottom - gap - margin;
    const above = rect.top - gap - margin;
    const useAbove = below < Math.min(preferredHeight, 176) && above > below;
    const maxHeight = Math.min(preferredHeight, Math.max(44, useAbove ? above : below));
    const top = useAbove
      ? Math.max(margin, rect.top - gap - maxHeight)
      : Math.min(viewportHeight - margin - maxHeight, rect.bottom + gap);
    return { left, maxHeight, top, width };
  }

  function addLabelContent(container, option) {
    const primary = document.createElement('span');
    primary.className = 'ui-select-primary';
    primary.textContent = option?.primary || '';
    container.appendChild(primary);
    if (option?.secondary) {
      const secondary = document.createElement('span');
      secondary.className = 'ui-select-secondary';
      secondary.textContent = option.secondary;
      container.appendChild(secondary);
    }
  }

  class SelectControl {
    constructor(select) {
      this.select = select;
      this.id = `ui-select-${++nextId}`;
      this.activeIndex = -1;
      this.typeahead = '';
      this.typeaheadTimer = null;
      this.positionFrame = null;
      this.labelListeners = [];
      this.query = '';
      this.searchPlaceholder = select.dataset.uiSelectSearch || '';

      this.shell = document.createElement('span');
      this.compact = select.hasAttribute('data-ui-select-compact');
      this.shell.className = `ui-select-shell${this.compact ? ' is-compact' : ''}`;
      select.parentNode.insertBefore(this.shell, select.nextSibling);

      this.trigger = document.createElement('button');
      this.trigger.type = 'button';
      this.trigger.className = 'ui-select-trigger';
      this.trigger.setAttribute('role', this.searchPlaceholder ? 'button' : 'combobox');
      if (!this.searchPlaceholder) this.trigger.setAttribute('aria-autocomplete', 'none');
      this.trigger.setAttribute('aria-expanded', 'false');
      this.trigger.setAttribute('aria-controls', `${this.id}-listbox`);
      this.trigger.setAttribute('aria-haspopup', 'listbox');
      this.value = document.createElement('span');
      this.value.className = 'ui-select-value';
      const chevron = document.createElement('span');
      chevron.className = 'ui-select-chevron';
      chevron.setAttribute('aria-hidden', 'true');
      this.trigger.append(this.value, chevron);
      this.shell.appendChild(this.trigger);

      const label = select.getAttribute('aria-label') || select.labels?.[0]?.textContent.trim() || 'Choose an option';
      this.trigger.setAttribute('aria-label', label);
      select.classList.add('ui-select-native');
      select.tabIndex = -1;
      select.setAttribute('aria-hidden', 'true');
      select.dataset.uiSelectReady = 'true';

      this.onTriggerClick = () => this.toggle();
      this.onTriggerMouseDown = (event) => {
        if (this.compact) event.preventDefault();
      };
      this.onKeyDown = (event) => this.handleKeyDown(event);
      this.onSelectChange = () => this.refresh();
      this.onOutsidePointer = (event) => {
        if (!this.shell.contains(event.target) && !this.popup?.contains(event.target)) this.close();
      };
      this.onViewportChange = () => this.schedulePosition();
      this.onRefresh = () => this.refresh();
      this.trigger.addEventListener('click', this.onTriggerClick);
      this.trigger.addEventListener('mousedown', this.onTriggerMouseDown);
      this.trigger.addEventListener('keydown', this.onKeyDown);
      select.addEventListener('change', this.onSelectChange);
      select.addEventListener('input', this.onSelectChange);
      select.addEventListener('ui-select:refresh', this.onRefresh);
      Array.from(select.labels || []).forEach((labelElement) => {
        const handler = (event) => {
          event.preventDefault();
          this.trigger.focus();
        };
        labelElement.addEventListener('click', handler);
        this.labelListeners.push([labelElement, handler]);
      });
      this.observer = new MutationObserver(() => this.refresh());
      this.observer.observe(select, { attributes: true, childList: true, characterData: true, subtree: true });
      this.refresh();
    }

    refresh() {
      this.options = optionRecords(this.select);
      const selected = this.options.find((option) => option.selected) || this.options[0];
      this.value.replaceChildren();
      addLabelContent(this.value, selected);
      this.trigger.title = this.select.title || selected?.label || '';
      this.trigger.disabled = this.select.disabled;
      if (this.select.disabled && this === openSelect) this.close();
      if (this.popup) this.renderOptions();
    }

    toggle() {
      if (this.trigger.disabled) return;
      if (this === openSelect) this.close();
      else this.open();
    }

    open(preferredIndex) {
      if (openSelect && openSelect !== this) openSelect.close();
      this.refresh();
      this.popup = document.createElement('div');
      this.popup.id = `${this.id}-popup`;
      this.popup.className = 'ui-select-popup';
      if (this.searchPlaceholder) {
        this.searchWrap = document.createElement('div');
        this.searchWrap.className = 'ui-select-search-wrap';
        this.searchInput = document.createElement('input');
        this.searchInput.type = 'search';
        this.searchInput.className = 'ui-select-search';
        this.searchInput.placeholder = this.searchPlaceholder;
        this.searchInput.setAttribute('role', 'combobox');
        this.searchInput.setAttribute('aria-label', this.searchPlaceholder);
        this.searchInput.setAttribute('aria-autocomplete', 'list');
        this.searchInput.setAttribute('aria-controls', `${this.id}-listbox`);
        this.searchInput.setAttribute('aria-expanded', 'true');
        this.searchInput.setAttribute('autocomplete', 'off');
        this.searchInput.addEventListener('input', () => {
          this.query = this.searchInput.value;
          this.renderOptions();
          this.schedulePosition();
        });
        this.searchInput.addEventListener('keydown', this.onKeyDown);
        this.searchWrap.appendChild(this.searchInput);
        this.popup.appendChild(this.searchWrap);
      }
      this.results = document.createElement('div');
      this.results.id = `${this.id}-listbox`;
      this.results.className = 'ui-select-options';
      this.results.setAttribute('role', 'listbox');
      this.results.setAttribute('aria-label', this.trigger.getAttribute('aria-label'));
      this.popup.appendChild(this.results);
      document.body.appendChild(this.popup);
      openSelect = this;
      this.trigger.setAttribute('aria-expanded', 'true');
      const selectedIndex = this.options.findIndex((option) => option.selected && !option.disabled);
      this.activeIndex = preferredIndex ?? (selectedIndex >= 0 ? selectedIndex : edgeEnabledIndex(this.options, false));
      this.renderOptions();
      this.position();
      if (this.searchInput) this.searchInput.focus({ preventScroll: true });
      document.addEventListener('pointerdown', this.onOutsidePointer, true);
      window.addEventListener('resize', this.onViewportChange);
      window.addEventListener('scroll', this.onViewportChange, true);
    }

    renderOptions() {
      if (!this.results) return;
      this.visibleOptions = filterOptions(this.options, this.query);
      if (!this.visibleOptions.some((option) => option.index === this.activeIndex && !option.disabled)) {
        this.activeIndex = edgeEnabledIndex(this.visibleOptions, false);
      }
      this.results.replaceChildren();
      if (!this.visibleOptions.length) {
        const empty = document.createElement('div');
        empty.className = 'ui-select-empty';
        empty.setAttribute('role', 'status');
        empty.textContent = 'No matches';
        this.results.appendChild(empty);
        this.trigger.removeAttribute('aria-activedescendant');
        this.searchInput?.removeAttribute('aria-activedescendant');
        return;
      }
      this.visibleOptions.forEach((option) => {
        const row = document.createElement('div');
        row.id = `${this.id}-option-${option.index}`;
        row.className = `ui-select-option${option.index === this.activeIndex ? ' is-active' : ''}`;
        row.setAttribute('role', 'option');
        row.setAttribute('aria-selected', String(option.selected));
        row.setAttribute('aria-disabled', String(option.disabled));
        row.dataset.index = String(option.index);
        const text = document.createElement('span');
        text.className = 'ui-select-option-label';
        addLabelContent(text, option);
        const check = document.createElement('span');
        check.className = 'ui-select-check';
        check.setAttribute('aria-hidden', 'true');
        check.classList.toggle('is-visible', option.selected);
        row.append(text, check);
        if (!option.disabled) {
          row.addEventListener('mousedown', (event) => event.preventDefault());
          row.addEventListener('pointermove', () => this.setActive(option.index));
          row.addEventListener('click', () => this.choose(option.index));
        }
        this.results.appendChild(row);
      });
      this.updateActiveDescendant();
    }

    setActive(index) {
      if (index < 0 || this.options[index]?.disabled) return;
      this.activeIndex = index;
      this.results?.querySelectorAll('.ui-select-option').forEach((row) => {
        row.classList.toggle('is-active', Number(row.dataset.index) === index);
      });
      this.updateActiveDescendant();
      this.results?.querySelector(`#${this.id}-option-${index}`)?.scrollIntoView({ block: 'nearest' });
    }

    updateActiveDescendant() {
      if (this.popup && this.activeIndex >= 0) {
        (this.searchInput || this.trigger).setAttribute('aria-activedescendant', `${this.id}-option-${this.activeIndex}`);
      }
    }

    choose(index) {
      if (index < 0 || this.options[index]?.disabled) return;
      const restoreTriggerFocus = Boolean(this.searchInput) || document.activeElement === this.trigger;
      this.select.selectedIndex = index;
      this.select.dispatchEvent(new Event('input', { bubbles: true }));
      this.select.dispatchEvent(new Event('change', { bubbles: true }));
      this.refresh();
      this.close();
      if (restoreTriggerFocus) this.trigger.focus();
    }

    handleKeyDown(event) {
      if (event.altKey || event.ctrlKey || event.metaKey) return;
      if (event.key === 'Tab') {
        if (event.target === this.searchInput) {
          event.preventDefault();
          this.focusAdjacent(event.shiftKey);
          return;
        }
        this.close();
        return;
      }
      if (event.key === 'Escape') {
        if (this === openSelect) {
          event.preventDefault();
          event.stopPropagation();
          this.close();
          this.trigger.focus();
        }
        return;
      }
      if (event.target === this.searchInput && event.key === ' ') return;
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        if (this === openSelect) this.choose(this.activeIndex);
        else this.open();
        return;
      }
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        if (this !== openSelect) this.open();
        else this.setActive(nextEnabledIndex(this.visibleOptions, this.activeIndex, event.key === 'ArrowDown' ? 1 : -1));
        return;
      }
      if (event.key === 'Home' || event.key === 'End') {
        event.preventDefault();
        if (this !== openSelect) this.open();
        this.setActive(edgeEnabledIndex(this.visibleOptions, event.key === 'End'));
        return;
      }
      if (event.target === this.searchInput) return;
      if (event.key.length === 1) {
        this.typeahead += event.key.toLocaleLowerCase();
        window.clearTimeout(this.typeaheadTimer);
        this.typeaheadTimer = window.setTimeout(() => { this.typeahead = ''; }, 650);
        if (this !== openSelect) this.open();
        this.setActive(typeaheadIndex(this.options, this.typeahead, this.activeIndex));
      }
    }

    schedulePosition() {
      if (this.positionFrame || this !== openSelect) return;
      this.positionFrame = window.requestAnimationFrame(() => {
        this.positionFrame = null;
        this.position();
      });
    }

    focusAdjacent(backwards) {
      const selector = 'a[href],button:not([disabled]),input:not([disabled]),textarea:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])';
      const focusable = Array.from(document.querySelectorAll(selector)).filter((element) => {
        return element !== this.searchInput && !element.hidden && element.getClientRects().length;
      });
      const index = focusable.indexOf(this.trigger);
      const target = focusable[index + (backwards ? -1 : 1)];
      this.close();
      (target || this.trigger).focus();
    }

    position() {
      if (!this.popup) return;
      const borderHeight = this.popup.offsetHeight - this.popup.clientHeight;
      const contentHeight = this.results.scrollHeight + (this.searchWrap?.offsetHeight || 0) + borderHeight;
      const popupHeight = Math.min(288, Math.max(44, contentHeight));
      const placement = popupPlacement(this.trigger.getBoundingClientRect(), window.innerWidth, window.innerHeight, popupHeight);
      Object.assign(this.popup.style, {
        left: `${placement.left}px`,
        maxHeight: `${placement.maxHeight}px`,
        top: `${placement.top}px`,
        width: `${placement.width}px`
      });
    }

    close() {
      if (this !== openSelect && !this.popup) return;
      document.removeEventListener('pointerdown', this.onOutsidePointer, true);
      window.removeEventListener('resize', this.onViewportChange);
      window.removeEventListener('scroll', this.onViewportChange, true);
      if (this.positionFrame) window.cancelAnimationFrame(this.positionFrame);
      this.positionFrame = null;
      this.popup?.remove();
      this.popup = null;
      this.results = null;
      this.searchWrap = null;
      this.searchInput?.removeAttribute('aria-activedescendant');
      this.searchInput = null;
      this.visibleOptions = [];
      this.query = '';
      this.trigger.removeAttribute('aria-activedescendant');
      this.trigger.setAttribute('aria-expanded', 'false');
      if (openSelect === this) openSelect = null;
    }

    destroy() {
      this.close();
      this.observer.disconnect();
      window.clearTimeout(this.typeaheadTimer);
      this.trigger.removeEventListener('click', this.onTriggerClick);
      this.trigger.removeEventListener('mousedown', this.onTriggerMouseDown);
      this.trigger.removeEventListener('keydown', this.onKeyDown);
      this.select.removeEventListener('change', this.onSelectChange);
      this.select.removeEventListener('input', this.onSelectChange);
      this.select.removeEventListener('ui-select:refresh', this.onRefresh);
      this.labelListeners.forEach(([label, handler]) => label.removeEventListener('click', handler));
      this.select.classList.remove('ui-select-native');
      this.select.removeAttribute('aria-hidden');
      this.select.removeAttribute('data-ui-select-ready');
      this.select.removeAttribute('tabindex');
      this.shell.remove();
      instances.delete(this);
    }
  }

  function initialize(root) {
    const selects = root.matches?.('select[data-ui-select]')
      ? [root]
      : Array.from(root.querySelectorAll?.('select[data-ui-select]') || []);
    selects.forEach((select) => {
      if (!select.dataset.uiSelectReady) instances.add(new SelectControl(select));
    });
  }

  function cleanup(root) {
    Array.from(instances).forEach((instance) => {
      if (instance.select === root || root.contains?.(instance.select)) instance.destroy();
    });
  }

  document.addEventListener('DOMContentLoaded', () => initialize(document));
  document.addEventListener('htmx:afterSwap', (event) => window.requestAnimationFrame(() => initialize(event.target)));
  document.addEventListener('htmx:beforeCleanupElement', (event) => cleanup(event.target));

  window.TalliSelect = { cleanup, initialize };
  window.TalliSelectTest = { edgeEnabledIndex, filterOptions, nextEnabledIndex, popupPlacement, splitLabel, typeaheadIndex };
})();
