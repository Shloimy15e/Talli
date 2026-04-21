(function () {
  'use strict';

  const FORMATS = {
    short:   { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' },
    long:    { year: 'numeric', month: 'long',  day: 'numeric', hour: '2-digit', minute: '2-digit' },
    date:    { year: 'numeric', month: 'short', day: 'numeric' },
    time:    { hour: '2-digit', minute: '2-digit' },
    full:    { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' },
    weekday: { weekday: 'short', month: 'short', day: 'numeric' },
    hm:      { hour: '2-digit', minute: '2-digit' },
  };

  const TOOLTIP_OPTS = {
    weekday: 'short', year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  };

  function fmt(date, preset) {
    const opts = FORMATS[preset] || FORMATS.short;
    return new Intl.DateTimeFormat(undefined, opts).format(date);
  }

  function relative(date) {
    const diffS = Math.round((date.getTime() - Date.now()) / 1000);
    const abs = Math.abs(diffS);
    const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
    if (abs < 45)        return rtf.format(diffS, 'second');
    if (abs < 2700)      return rtf.format(Math.round(diffS / 60), 'minute');
    if (abs < 86400)     return rtf.format(Math.round(diffS / 3600), 'hour');
    if (abs < 2592000)   return rtf.format(Math.round(diffS / 86400), 'day');
    if (abs < 31536000)  return rtf.format(Math.round(diffS / 2592000), 'month');
    return rtf.format(Math.round(diffS / 31536000), 'year');
  }

  function renderDatetimes(root) {
    root.querySelectorAll('[data-iso]').forEach(el => {
      const date = new Date(el.dataset.iso);
      if (isNaN(date)) return;
      el.textContent = fmt(date, el.dataset.format);
    });
  }

  function renderTimeagos(root) {
    root.querySelectorAll('[data-iso-timeago]').forEach(el => {
      const date = new Date(el.dataset.isoTimeago);
      if (isNaN(date)) return;
      el.textContent = relative(date);
      el.setAttribute('data-tippy-content', new Intl.DateTimeFormat(undefined, TOOLTIP_OPTS).format(date));
    });
  }

  function renderAll(root) {
    root = root || document.body;
    renderDatetimes(root);
    renderTimeagos(root);
  }

  // Expose so layout can call before initTooltips() so Tippy picks up device-tz content.
  window.renderTimes = renderAll;

  document.addEventListener('DOMContentLoaded', () => renderAll());
  document.addEventListener('htmx:afterSwap', e => renderAll(e.target));

  // Keep relative labels fresh without re-reading data-format elements (which don't change).
  setInterval(() => renderTimeagos(document.body), 60000);
})();
