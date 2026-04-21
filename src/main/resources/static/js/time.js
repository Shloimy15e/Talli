(function () {
  'use strict';

  const FORMATS = {
    short:   { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' },
    long:    { year: 'numeric', month: 'long',  day: 'numeric', hour: '2-digit', minute: '2-digit' },
    date:    { year: 'numeric', month: 'short', day: 'numeric' },
    md:      { month: 'short', day: 'numeric' },
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

  const DATE_TOOLTIP_OPTS = {
    weekday: 'short', year: 'numeric', month: 'short', day: 'numeric'
  };

  function renderDateAgos(root) {
    const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    root.querySelectorAll('[data-iso-dateago]').forEach(el => {
      const s = el.dataset.isoDateago;
      if (!s) return;
      const [y, m, d] = s.split('-').map(Number);
      if (!y || !m || !d) return;
      const target = new Date(y, m - 1, d);
      const days = Math.round((target.getTime() - startOfToday) / 86400000);
      el.textContent = rtf.format(days, 'day');
      el.setAttribute('data-tippy-content', new Intl.DateTimeFormat(undefined, DATE_TOOLTIP_OPTS).format(target));
    });
  }

  // <input type="datetime-local" data-utc-input> contains a naive UTC wall-clock
  // (yyyy-MM-ddTHH:mm) rendered by the server. On load we convert to device-local
  // so the user edits in their own tz; on submit we convert back to UTC before POST.
  const pad = n => String(n).padStart(2, '0');
  const toLocalInput = d =>
    `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  const toUtcInput = d =>
    `${d.getUTCFullYear()}-${pad(d.getUTCMonth()+1)}-${pad(d.getUTCDate())}T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;

  function hydrateUtcInputs(root) {
    root.querySelectorAll('input[type="datetime-local"][data-utc-input]').forEach(inp => {
      if (inp.dataset.utcHydrated || !inp.value) return;
      // inp.value is the naive UTC wall-clock from the server (e.g. "2026-04-21T07:30").
      // Appending 'Z' makes new Date() parse it as UTC; getters then yield device-local.
      const d = new Date(inp.value + 'Z');
      if (isNaN(d)) return;
      inp.value = toLocalInput(d);
      inp.dataset.utcHydrated = '1';
      // Let Alpine x-model (if any) re-read the new value.
      inp.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  // Global submit interceptor: for any data-utc-input in the submitting form,
  // rewrite the device-local value back to a naive UTC wall-clock before the POST.
  document.addEventListener('submit', e => {
    const form = e.target;
    if (!(form instanceof HTMLFormElement)) return;
    form.querySelectorAll('input[type="datetime-local"][data-utc-input]').forEach(inp => {
      if (!inp.value) return;
      const d = new Date(inp.value); // parsed as device-local by the browser
      if (isNaN(d)) return;
      inp.value = toUtcInput(d);
    });
  }, true);

  function renderAll(root) {
    root = root || document.body;
    renderDatetimes(root);
    renderTimeagos(root);
    renderDateAgos(root);
    hydrateUtcInputs(root);
  }

  // Expose so layout can call before initTooltips() so Tippy picks up device-tz content.
  window.renderTimes = renderAll;

  document.addEventListener('DOMContentLoaded', () => renderAll());
  document.addEventListener('htmx:afterSwap', e => renderAll(e.target));

  // Keep relative labels fresh without re-reading data-format elements (which don't change).
  setInterval(() => renderTimeagos(document.body), 60000);
})();
