(function () {
  'use strict';

  const STORAGE_KEY = 'talli.sidebar.collapsed';
  const DESKTOP_QUERY = '(min-width: 1024px)';

  const BRAND_LINKS = '.app-content a.hover\\:underline:not(.app-icon-button):not([class*="bg-"]), '
    + '.portal-app a.hover\\:underline:not(.app-icon-button):not([class*="bg-"]), '
    + '.auth-page a.hover\\:underline:not([class*="bg-"]), '
    + '.app-nav-link, .mail-text-link, .mail-conversation-context a, .mail-folders-bottom a';

  function initializeBrandLinks(root) {
    const links = Array.from(root.querySelectorAll(BRAND_LINKS));
    if (root.matches?.(BRAND_LINKS)) links.unshift(root);
    links.forEach((link) => {
      if (link.querySelector('.app-link-glyph') || link.closest('[role="menu"], .ui-select')) return;
      const label = link.querySelector('.app-nav-label') || link;
      const walker = document.createTreeWalker(label, NodeFilter.SHOW_TEXT);
      const nodes = [];
      while (walker.nextNode()) {
        const node = walker.currentNode;
        if (node.textContent.trim() && !node.parentElement.closest('svg, [aria-hidden="true"], .sr-only')) nodes.push(node);
      }
      if (!nodes.length) return;
      const style = window.getComputedStyle(label);
      link.style.setProperty('--app-link-rest', style.color);
      link.style.setProperty('--app-link-weight', style.fontWeight);
      const length = nodes.reduce((total, node) => total + Array.from(node.textContent.replace(/\s+/g, ' ')).length, 0);
      const step = Math.min(18, 180 / Math.max(length - 1, 1));
      let index = 0;
      nodes.forEach((node) => {
        const text = node.textContent.replace(/\s+/g, ' ');
        const accessible = document.createElement('span');
        accessible.className = 'app-link-accessible';
        accessible.textContent = text;
        const visual = document.createElement('span');
        visual.setAttribute('aria-hidden', 'true');
        text.split(/(\s+)/).forEach((word) => {
          const wordSpan = document.createElement('span');
          wordSpan.className = 'app-link-word';
          Array.from(word).forEach((letter) => {
            const character = document.createElement('span');
            character.className = 'app-link-letter';
            character.style.setProperty('--letter-in', `${index * step}ms`);
            character.style.setProperty('--letter-out', `${(length - 1 - index) * step}ms`);
            index++;
            const width = document.createElement('span');
            width.className = 'app-link-width';
            width.textContent = letter;
            const glyph = document.createElement('span');
            glyph.className = 'app-link-glyph';
            glyph.textContent = letter;
            character.append(width, glyph);
            wordSpan.append(character);
          });
          visual.append(wordSpan);
        });
        node.replaceWith(accessible, visual);
      });
      link.classList.add('app-brand-link');
    });
  }

  document.addEventListener('htmx:afterSwap', (event) => initializeBrandLinks(event.detail.target));

  // Keep record navigation on its real link or editor button, including HTMX swaps.
  document.addEventListener('click', (event) => {
    if (event.defaultPrevented || event.button !== 0
      || event.target.closest('a,button,input,select,textarea,label,form,summary,[contenteditable="true"],[role="button"]')
      || window.getSelection()?.toString()) return;

    const row = event.target.closest('[data-row-action]');
    const primary = row?.querySelector('[data-row-primary]');
    if (!primary || primary.matches(':disabled, [aria-disabled="true"]')) return;
    if (primary.tagName !== 'A' && (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)) return;

    primary.dispatchEvent(new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
      view: window,
      ctrlKey: event.ctrlKey,
      metaKey: event.metaKey,
      shiftKey: event.shiftKey,
      altKey: event.altKey,
    }));
  });

  function storedCollapsed() {
    try {
      return window.localStorage.getItem(STORAGE_KEY) === 'true';
    } catch (error) {
      return false;
    }
  }

  function storeCollapsed(value) {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(value));
    } catch (error) {
      // The shell still works when storage is unavailable.
    }
  }

  function setTooltip(button, label) {
    button.setAttribute('aria-label', label);
    button.setAttribute('title', label);
    button.setAttribute('data-tippy-content', label);
    if (button._tippy) button._tippy.setContent(label);
  }

  function initializeShell() {
    const shell = document.querySelector('.app-shell');
    const sidebar = document.getElementById('app-sidebar');
    if (!shell || !sidebar || shell.dataset.appShellReady === 'true') return;

    shell.dataset.appShellReady = 'true';
    const desktop = window.matchMedia(DESKTOP_QUERY);
    const collapseButton = sidebar.querySelector('[data-app-sidebar-collapse]');
    const drawerButtons = Array.from(document.querySelectorAll('[data-app-sidebar-open]'));
    const closeButtons = Array.from(document.querySelectorAll('[data-app-sidebar-close]'));
    const navigationTooltipTargets = Array.from(sidebar.querySelectorAll('.app-sidebar-brand, .app-nav-link, .app-nav-action'));
    let returnFocus = null;

    function syncNavigationTooltips() {
      const enabled = desktop.matches && document.documentElement.dataset.sidebarCollapsed === 'true';
      navigationTooltipTargets.forEach((target) => {
        if (!target._tippy) return;
        if (enabled) target._tippy.enable();
        else target._tippy.disable();
      });
    }

    function setCollapsed(collapsed, persist) {
      document.documentElement.dataset.sidebarCollapsed = String(collapsed);
      shell.dataset.sidebarCollapsed = String(collapsed);
      if (collapseButton) {
        collapseButton.setAttribute('aria-expanded', String(!collapsed));
        setTooltip(collapseButton, collapsed ? 'Expand navigation' : 'Collapse navigation');
      }
      syncNavigationTooltips();
      requestAnimationFrame(syncNavigationTooltips);
      if (persist) storeCollapsed(collapsed);
    }

    function syncSidebarAvailability() {
      if (desktop.matches) {
        sidebar.removeAttribute('inert');
        sidebar.removeAttribute('aria-hidden');
        closeDrawer(false);
      } else if (shell.dataset.sidebarDrawerOpen !== 'true') {
        sidebar.setAttribute('inert', '');
        sidebar.setAttribute('aria-hidden', 'true');
      }
      syncNavigationTooltips();
    }

    function openDrawer(trigger) {
      if (desktop.matches) return;
      returnFocus = trigger || document.activeElement;
      shell.dataset.sidebarDrawerOpen = 'true';
      sidebar.removeAttribute('inert');
      sidebar.removeAttribute('aria-hidden');
      drawerButtons.forEach((button) => button.setAttribute('aria-expanded', 'true'));
      requestAnimationFrame(() => {
        const target = sidebar.querySelector('[aria-current="page"]') || sidebar.querySelector('a, button');
        if (target) target.focus();
      });
    }

    function closeDrawer(restoreFocus) {
      const wasOpen = shell.dataset.sidebarDrawerOpen === 'true';
      shell.dataset.sidebarDrawerOpen = 'false';
      drawerButtons.forEach((button) => button.setAttribute('aria-expanded', 'false'));
      if (!desktop.matches) {
        sidebar.setAttribute('inert', '');
        sidebar.setAttribute('aria-hidden', 'true');
      }
      if (wasOpen && restoreFocus && returnFocus && typeof returnFocus.focus === 'function') {
        returnFocus.focus();
      }
    }

    setCollapsed(storedCollapsed(), false);

    if (collapseButton) {
      collapseButton.addEventListener('click', () => {
        const collapsed = document.documentElement.dataset.sidebarCollapsed !== 'true';
        setCollapsed(collapsed, true);
      });
    }

    drawerButtons.forEach((button) => button.addEventListener('click', () => openDrawer(button)));
    closeButtons.forEach((button) => button.addEventListener('click', () => closeDrawer(true)));
    sidebar.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', () => {
        if (!desktop.matches) closeDrawer(false);
      });
    });

    document.addEventListener('keydown', (event) => {
      if (shell.dataset.sidebarDrawerOpen !== 'true') return;
      if (event.key === 'Escape') {
        event.preventDefault();
        closeDrawer(true);
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = Array.from(sidebar.querySelectorAll('a[href], button:not([disabled])'))
        .filter((element) => !element.hasAttribute('inert') && element.offsetParent !== null);
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    });

    desktop.addEventListener('change', syncSidebarAvailability);
    syncSidebarAvailability();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      initializeBrandLinks(document);
      initializeShell();
    }, { once: true });
  } else {
    initializeBrandLinks(document);
    initializeShell();
  }
})();
