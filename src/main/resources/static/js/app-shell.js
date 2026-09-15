(function () {
  'use strict';

  const STORAGE_KEY = 'talli.sidebar.collapsed';
  const DESKTOP_QUERY = '(min-width: 1024px)';

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
    document.addEventListener('DOMContentLoaded', initializeShell, { once: true });
  } else {
    initializeShell();
  }
})();
