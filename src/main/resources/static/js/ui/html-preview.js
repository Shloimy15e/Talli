(() => {
  const escapeHtml = (value) => value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');

  const signatureStyles = () =>
    document.getElementById('email-signature-styles')?.textContent?.trim() || '';

  const previewDocument = (body) => `<!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline'">
        <style>
          body { margin: 28px; color: #334155; font: 14px/1.65 Arial, Helvetica, sans-serif; overflow-wrap: anywhere; }
          img { max-width: 100%; height: auto; }
          table { max-width: 100%; }
          ${signatureStyles()}
          @media (max-width: 520px) {
            body { margin: 20px; }
          }
        </style>
      </head>
      <body>${body}</body>
    </html>`;

  const init = () => {
    const dialog = document.querySelector('[data-html-preview-dialog]');
    const frame = dialog?.querySelector('[data-html-preview-frame]');
    if (!dialog || !frame || dialog.dataset.htmlPreviewReady) return;

    const heading = dialog.querySelector('[data-html-preview-heading]');
    const subtitle = dialog.querySelector('[data-html-preview-subtitle]');
    const closeButton = dialog.querySelector('[data-html-preview-close]');
    let opener = null;
    let closeTimer = null;

    const finishClose = () => {
      window.clearTimeout(closeTimer);
      closeTimer = null;
      if (dialog.open) dialog.close();
    };

    const close = () => {
      if (!dialog.open || dialog.dataset.closing) return;
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        finishClose();
        return;
      }

      dialog.dataset.closing = 'true';
      closeTimer = window.setTimeout(finishClose, 200);
    };

    const htmlFor = (trigger) => {
      const sourceSelector = trigger.dataset.htmlPreviewSource;
      if (!sourceSelector) return trigger.dataset.htmlPreviewHtml || '';

      const source = document.querySelector(sourceSelector);
      const value = source?.value || '';
      const formatName = trigger.dataset.htmlPreviewFormat;
      const selectedFormat = formatName
        ? document.querySelector(`input[name="${CSS.escape(formatName)}"]:checked`)?.value
        : 'html';
      return selectedFormat === 'text'
        ? escapeHtml(value).replace(/\r?\n/g, '<br>')
        : value;
    };

    const open = (trigger) => {
      opener = trigger;
      heading.textContent = trigger.dataset.htmlPreviewTitle || 'HTML preview';
      subtitle.textContent = trigger.dataset.htmlPreviewSubtitle || '';
      subtitle.hidden = !subtitle.textContent;
      frame.title = heading.textContent;
      frame.srcdoc = previewDocument(htmlFor(trigger));
      dialog.showModal();
      closeButton.focus();
    };

    document.querySelectorAll('[data-html-preview-trigger]').forEach((trigger) => {
      trigger.addEventListener('click', () => open(trigger));
    });

    closeButton.addEventListener('click', close);
    dialog.addEventListener('click', (event) => {
      const bounds = dialog.getBoundingClientRect();
      const inside = event.clientX >= bounds.left && event.clientX <= bounds.right
        && event.clientY >= bounds.top && event.clientY <= bounds.bottom;
      if (!inside) close();
    });
    dialog.addEventListener('cancel', (event) => {
      event.preventDefault();
      close();
    });
    dialog.addEventListener('transitionend', (event) => {
      if (event.target === dialog && event.propertyName === 'opacity' && dialog.dataset.closing) {
        finishClose();
      }
    });
    dialog.addEventListener('close', () => {
      window.clearTimeout(closeTimer);
      closeTimer = null;
      delete dialog.dataset.closing;
      frame.srcdoc = '';
      const focusTarget = opener;
      opener = null;
      window.requestAnimationFrame(() => focusTarget?.focus({ preventScroll: true }));
    });
    dialog.dataset.htmlPreviewReady = 'true';
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => init());
  } else {
    init();
  }
})();
