(() => {
  'use strict';
  const listPositions = new Map();
  const mailboxKey = () => new URL(location.href).searchParams.get('mailboxAddress') + ':' + new URL(location.href).searchParams.get('folder') + ':' + new URL(location.href).searchParams.get('search');

  function fitMessage(frame) {
    // Scripts stay disabled in the sandbox. Same-origin access lets the trusted
    // reader measure content without letting a message run code in the app.
    try {
      const doc = frame.contentDocument;
      if (!doc?.body || frame.dataset.fitted === 'true') return;
      frame.dataset.fitted = 'true';
      const style = doc.createElement('style');
      style.textContent = 'html{overflow:hidden}body{margin:0!important;overflow-wrap:anywhere}img{max-width:100%;height:auto}table{max-width:100%}';
      doc.head.append(style);
      doc.querySelectorAll('a[href]').forEach(link => {
        const href = link.getAttribute('href').trim();
        if (/^(https?:|mailto:|tel:|#)/i.test(href)) {
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
        } else link.removeAttribute('href');
      });
      const resize = () => {
        if (!frame.isConnected || !frame.closest('details')?.open) return;
        const height = Math.max(80, Math.ceil(doc.body.scrollHeight), Math.ceil(doc.body.getBoundingClientRect().height));
        if (Math.abs(frame.getBoundingClientRect().height - height) > 2) frame.style.height = height + 'px';
      };
      const observer = new ResizeObserver(resize);
      observer.observe(doc.body);
      frame.closest('details')?.addEventListener('toggle', resize);
      doc.querySelectorAll('img').forEach(img => img.addEventListener('load', resize, {once:true}));
      resize();
    } catch (_) {
      // A message that navigates its own frame may become cross-origin.
      frame.style.height = '420px';
    }
  }

  function initializeMail() {
    const workspace = document.getElementById('mail-workspace');
    if (!workspace) return;
    workspace.querySelectorAll('.mail-message-frame').forEach(frame => {
      if (frame.dataset.listening) return;
      frame.dataset.listening = 'true';
      frame.addEventListener('load', () => { delete frame.dataset.fitted; fitMessage(frame); });
      if (frame.contentDocument?.readyState === 'complete') fitMessage(frame);
    });
    const list = document.getElementById('mail-list-scroll');
    if (list) list.scrollTop = listPositions.get(mailboxKey()) || 0;
  }

  document.addEventListener('click', event => {
    const workspace = document.getElementById('mail-workspace');
    if (!workspace) return;
    if (event.target.closest('[data-toggle-folders]')) workspace.classList.toggle('folders-open');
    if (event.target.closest('[data-refresh-mail]')) location.reload();
    const expand = event.target.closest('[data-expand-messages]');
    if (expand) {
      const messages = [...workspace.querySelectorAll('.mail-message')];
      const open = messages.some(message => !message.open);
      messages.forEach(message => { message.open = open; });
      expand.textContent = open ? 'Collapse all' : 'Expand all';
    }
  });

  document.addEventListener('keydown', event => {
    const workspace = document.getElementById('mail-workspace');
    if (!workspace || event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return;
    if (event.target.closest('input,textarea,select,[contenteditable="true"],#modal,#reply-composer')) return;
    if (event.key.toLowerCase() === 'c') {
      event.preventDefault();
      workspace.querySelector('.mail-compose-button')?.click();
    } else if (event.key.toLowerCase() === 'r') {
      event.preventDefault();
      workspace.querySelector('.mail-reply-prompt')?.click();
    } else if (event.key === '/' && !event.shiftKey) {
      event.preventDefault(); document.getElementById('mail-search')?.focus();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      const rows = [...workspace.querySelectorAll('.mail-row')];
      const current = rows.findIndex(row => row.classList.contains('is-selected'));
      const next = current < 0 ? 0 : current + (event.key === 'ArrowDown' ? 1 : -1);
      if (rows[next]) { event.preventDefault(); rows[next].querySelector('.mail-row-link').click(); }
    }
  });

  document.addEventListener('htmx:beforeRequest', () => {
    const list = document.getElementById('mail-list-scroll');
    if (list) listPositions.set(mailboxKey(), list.scrollTop);
  });
  document.addEventListener('htmx:afterSwap', initializeMail);
  document.addEventListener('htmx:responseError', () => {
    const error = document.querySelector('.mail-network-error');
    if (error) error.hidden = false;
  });
  document.addEventListener('htmx:sendError', () => {
    const error = document.querySelector('.mail-network-error');
    if (error) error.hidden = false;
  });
  document.addEventListener('DOMContentLoaded', initializeMail);
})();
