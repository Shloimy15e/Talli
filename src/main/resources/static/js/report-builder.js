(() => {
  'use strict';
  const root = document.getElementById('report-builder');
  if (!root) return;
  const $ = id => document.getElementById(`rb-${id}`);
  const clone = value => JSON.parse(JSON.stringify(value));
  let catalog, query, executed = null, result = null, busy = false, dirty = true;
  let serial = 0;
  const presets = new Map();
  let summaryQuery = null;
  const operators = {eq: 'Equals', ne: 'Does not equal', in: 'Is one of', contains: 'Contains', gte: 'At least / on or after', lte: 'At most / on or before', is_null: 'Is empty', is_not_null: 'Is not empty'};
  const functions = {sum: 'Sum', count: 'Count', avg: 'Average', min: 'Minimum', max: 'Maximum'};
  function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  function error(message) { $('error').textContent = message; $('error').hidden = !message; }
  function dataset() { return catalog.find(item => item.key === query.dataset); }
  function fields(all = false) { return (dataset()?.fields || []).filter(field => all || !field.filterOnly); }
  function fieldLabel(key) { return dataset()?.fields.find(field => field.key === key)?.label || key; }
  function select(options, value, change) {
    const node = el('select');
    for (const [key, label] of options) { const option = el('option', label); option.value = key; node.append(option); }
    // Retain supplied values so a linked report is never silently rewritten.
    if (value !== undefined && !options.some(([key]) => key === value)) { const option = el('option', value); option.value = value; node.append(option); }
    node.value = value ?? options[0]?.[0] ?? '';
    node.addEventListener('change', () => { change(node.value); changed(); });
    return node;
  }
  function label(text, control) { const node = el('label'); control.id ||= `rb-control-${++serial}`; node.htmlFor = control.id; if (control.type === 'checkbox') node.append(control, el('span', text)); else node.append(el('span', text), control); return node; }
  function button(text, action, className = '') { const node = el('button', text, `rb-button ${className}`); node.type = 'button'; node.addEventListener('click', action); return node; }
  function changed() {
    dirty = true;
    $('change-status').textContent = executed ? 'Settings changed. Run report to update results and exports.' : 'Run your report to view results.';
    updateActions();
  }
  function updateActions() {
    for (const control of root.querySelectorAll('#rb-form input, #rb-form select, #rb-form button, #rb-library button')) control.disabled = busy;
    for (const key of ['copy', 'csv', 'xlsx']) $(key).disabled = busy || dirty || !executed;
    $('run').disabled = busy;
    $('previous').disabled = busy || dirty || !executed || !Number(executed.offset);
    $('next').disabled = busy || dirty || result?.nextOffset == null;
    root.querySelector('.rb-results').setAttribute('aria-busy', String(busy));
  }
  function fresh(key) {
    const item = catalog.find(entry => entry.key === key) || catalog[0];
    const today = new Date();
    const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return {dataset: item.key, from: `${today.getFullYear()}-01-01`, to: date, columns: [...item.defaultColumns], where: {match: 'all', filters: [], groups: []}, groupBy: [], metrics: [], sort: [], offset: 0, limit: 50};
  }
  function buildPresets() {
    const add = (key, title, datasetKey, configure = () => {}) => {
      if (!catalog.some(item => item.key === datasetKey)) return;
      presets.set(key, () => { const spec = fresh(datasetKey); configure(spec); return spec; });
      const option = el('option', title); option.value = key; $('preset').append(option);
      const section = datasetKey === 'invoices' ? 'Collections' : datasetKey === 'payments' || datasetKey === 'expenses' ? 'Money movement' : 'Client and project work';
      let group = [...$('library').children].find(node => node.dataset.section === section);
      if (!group) { group = el('div', undefined, 'rb-library-group'); group.dataset.section = section; group.append(el('h3', section)); $('library').append(group); }
      const shortcut = button(title.replace(' · all issued dates', ''), () => { choosePreset(key); run(clone(query)); }, 'rb-library-link'); shortcut.dataset.preset = key; group.append(shortcut);
    };
    const filter = (field, value) => ({field, operator: 'eq', values: [value]});
    const summary = (spec, groups, metrics) => { spec.groupBy = groups; spec.columns = [...groups]; spec.metrics = metrics.map(field => ({field, function: 'sum'})); spec.sort = [{field: groups[0], direction: 'asc'}]; };
    add('invoices', 'Invoice register', 'invoices');
    add('outstanding', 'Outstanding invoices · all issued dates', 'invoices', spec => { spec.from = '1900-01-01'; spec.where.filters = [filter('outstanding', 'true')]; spec.sort = [{field: 'dueAt', direction: 'asc'}]; });
    add('aging', 'Receivables aging · all issued dates', 'invoices', spec => { spec.from = '1900-01-01'; spec.where.filters = [filter('outstanding', 'true')]; summary(spec, ['agingBucket', 'currency'], ['balance']); });
    add('client-invoices', 'Invoice summary by client', 'invoices', spec => { summary(spec, ['clientId', 'clientName', 'currency'], ['amount', 'amountPaid', 'balance']); spec.where.filters = [{field: 'status', operator: 'ne', values: ['void']}]; });
    add('payments', 'Payment register', 'payments');
    add('cash-receipts', 'Cash receipts · direct payments', 'payments', spec => { spec.where.filters = [filter('source', 'direct')]; });
    add('monthly-receipts', 'Monthly cash receipts', 'payments', spec => { spec.where.filters = [filter('source', 'direct')]; summary(spec, ['month', 'currency'], ['amount']); });
    add('expenses', 'Expense register', 'expenses');
    add('expense-category', 'Expenses by category', 'expenses', spec => summary(spec, ['category', 'currency'], ['amount']));
    add('expense-project', 'Expenses by project', 'expenses', spec => summary(spec, ['clientName', 'projectId', 'projectName', 'currency'], ['amount']));
    add('unbilled-expenses', 'Unbilled billable expenses', 'expenses', spec => { spec.where.filters = [filter('billable', 'true'), filter('billed', 'false')]; });
    add('time_entries', 'Time register', 'time_entries');
    add('project-time', 'Hours and value by project', 'time_entries', spec => summary(spec, ['clientName', 'projectId', 'projectName', 'currency'], ['hours', 'value']));
    add('unbilled-time', 'Unbilled completed billable time', 'time_entries', spec => { spec.where.filters = [filter('billable', 'true'), filter('billed', 'false'), filter('running', 'false'), filter('rateType', 'hourly')]; });
  }
  function choosePreset(key) {
    query = presets.get(key)(); summaryQuery = null; $('back-summary').hidden = true; $('preset').value = key;
    $('report-title').textContent = [...$('preset').options].find(option => option.value === key).textContent;
    $('report-description').textContent = key === 'outstanding' || key === 'aging' ? 'Current unpaid balances, including invoices issued in prior years.' : 'Adjust the period, filters, and summary to fit your work.';
    for (const node of root.querySelectorAll('[data-preset]')) { node.classList.toggle('is-selected', node.dataset.preset === key); node.setAttribute('aria-current', node.dataset.preset === key ? 'true' : 'false'); }
    hydrate(); changed();
  }
  function buildPeriods() {
    const date = value => `${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`;
    const today = new Date(), year = today.getFullYear(), month = today.getMonth();
    const ranges = [['This month', new Date(year, month, 1), today], ['Last month', new Date(year, month - 1, 1), new Date(year, month, 0)], ['This quarter', new Date(year, Math.floor(month / 3) * 3, 1), today], ['Year to date', new Date(year, 0, 1), today], ['All time', new Date(1900, 0, 1), today]];
    for (const [name, from, to] of ranges) $('periods').append(button(name, () => { query.from = date(from); query.to = date(to); hydrate(); changed(); }));
  }
  function ensureQuery() {
    query.columns ||= [...dataset().defaultColumns]; query.where ||= {match: 'all', filters: [], groups: []};
    query.groupBy ||= []; query.metrics ||= []; query.sort ||= []; query.offset ??= 0; query.limit ??= 50;
  }
  function renderGroup(group, depth = 0, remove) {
    group.filters ||= []; group.groups ||= []; group.match ||= 'all';
    const wrap = el('div', undefined, 'rb-filter-group');
    const toolbar = el('div', undefined, 'rb-group-toolbar');
    toolbar.append(label('Match', select([['all', 'All conditions (AND)'], ['any', 'Any condition (OR)']], group.match, value => { group.match = value; })));
    toolbar.append(button('Add condition', () => {
      const count = (current) => current.filters.length + current.groups.reduce((total, child) => total + count(child), 0);
      if (count(query.where) >= 50) { error('Use at most 50 filter conditions.'); return; }
      group.filters.push({field: fields(true)[0].key, operator: 'eq', values: ['']}); renderFilters(); changed();
    }));
    if (depth < 3) toolbar.append(button('Add group', () => { group.groups.push({match: 'all', filters: [], groups: []}); renderFilters(); changed(); }));
    if (remove) toolbar.append(button('Remove group', remove, 'rb-remove'));
    wrap.append(toolbar);
    group.filters.forEach((filter, index) => {
      const row = el('div', undefined, 'rb-filter-row');
      row.append(label('Field', select(fields(true).map(item => [item.key, item.label]), filter.field, value => { filter.field = value; renderFilters(); })));
      row.append(label('Condition', select(Object.entries(operators), filter.operator, value => { filter.operator = value; renderFilters(); })));
      if (!['is_null', 'is_not_null'].includes(filter.operator)) {
        const values = el('div', undefined, 'rb-values');
        filter.values ||= [''];
        if (!filter.values.length) filter.values.push('');
        filter.values.forEach((value, valueIndex) => {
          const control = el('input'); control.type = 'text'; control.value = value;
          const field = fields(true).find(item => item.key === filter.field);
          if (field?.type === 'date') { control.placeholder = 'YYYY-MM-DD'; control.inputMode = 'numeric'; }
          if (field?.numeric || field?.type === 'number') control.inputMode = 'decimal';
          if (field?.type === 'boolean') control.placeholder = 'true or false';
          control.addEventListener('input', () => { filter.values[valueIndex] = control.value; changed(); });
          const valueWrap = el('div', undefined, 'rb-value'); valueWrap.append(label(filter.values.length > 1 ? `Value ${valueIndex + 1}` : 'Value', control));
          if (filter.values.length > 1) valueWrap.append(button('Remove value', () => { filter.values.splice(valueIndex, 1); renderFilters(); changed(); }, 'rb-remove'));
          values.append(valueWrap);
        });
        if (filter.operator === 'in') values.append(button('Add value', () => { filter.values.push(''); renderFilters(); changed(); }));
        row.append(values);
      }
      row.append(button('Remove', () => { group.filters.splice(index, 1); renderFilters(); changed(); }, 'rb-remove')); wrap.append(row);
    });
    group.groups.forEach((child, index) => wrap.append(renderGroup(child, depth + 1, () => { group.groups.splice(index, 1); renderFilters(); changed(); })));
    return wrap;
  }
  function renderFilters() { $('filters').replaceChildren(renderGroup(query.where)); }
  function renderColumns() {
    $('columns').replaceChildren(); $('column-order').replaceChildren();
    $('column-count').textContent = `(${query.columns.length} selected)`;
    query.columns.forEach((key, index) => {
      const chip = el('div', undefined, 'rb-column-chip'); chip.append(el('span', fieldLabel(key)));
      if (index) chip.append(button('Move left', () => { [query.columns[index - 1], query.columns[index]] = [query.columns[index], query.columns[index - 1]]; renderColumns(); changed(); }));
      $('column-order').append(chip);
    });
    for (const field of fields()) {
      const input = el('input'); input.type = 'checkbox'; input.checked = query.columns.includes(field.key);
      input.addEventListener('change', () => { query.columns = input.checked ? [...query.columns, field.key] : query.columns.filter(key => key !== field.key); renderColumns(); changed(); });
      $('columns').append(label(field.label, input));
    }
  }
  function renderGrouping() {
    $('groupby').replaceChildren();
    for (const field of fields()) {
      const input = el('input'); input.type = 'checkbox'; input.checked = query.groupBy.includes(field.key);
      input.addEventListener('change', () => { query.groupBy = input.checked ? [...query.groupBy, field.key] : query.groupBy.filter(key => key !== field.key); query.columns = query.groupBy.length || query.metrics.length ? [...query.groupBy] : [...dataset().defaultColumns]; renderColumns(); renderSort(); changed(); });
      $('groupby').append(label(field.label, input));
    }
    $('metrics').replaceChildren();
    query.metrics.forEach((metric, index) => {
      const row = el('div', undefined, 'rb-calculation-row');
      const available = fields().filter(field => metric.function === 'count' || field.numeric || field.type === 'number');
      row.append(label('Calculation', select(Object.entries(functions), metric.function, value => { metric.function = value; renderGrouping(); renderSort(); })));
      row.append(label('Field', select(available.map(field => [field.key, field.label]), metric.field, value => { metric.field = value; renderSort(); })));
      row.append(button('Remove', () => { query.metrics.splice(index, 1); query.columns = query.groupBy.length || query.metrics.length ? [...query.groupBy] : [...dataset().defaultColumns]; renderColumns(); renderGrouping(); renderSort(); changed(); }, 'rb-remove'));
      $('metrics').append(row);
    });
  }
  function renderSort() {
    $('sort').replaceChildren();
    const grouped = query.groupBy.length || query.metrics.length;
    const options = fields().filter(field => !grouped || query.groupBy.includes(field.key)).map(field => [field.key, field.label]).concat(query.metrics.map(metric => [`${metric.function}_${metric.field}`, `${functions[metric.function]} of ${fieldLabel(metric.field)}`]));
    query.sort.forEach((sort, index) => {
      const row = el('div', undefined, 'rb-sort-row');
      row.append(label(index ? 'Then by' : 'Sort by', select(options, sort.field, value => { sort.field = value; })));
      row.append(label('Direction', select([['asc', 'Ascending'], ['desc', 'Descending']], sort.direction, value => { sort.direction = value; })));
      row.append(button('Remove', () => { query.sort.splice(index, 1); renderSort(); changed(); }, 'rb-remove')); $('sort').append(row);
    });
  }
  function hydrate() {
    ensureQuery(); $('dataset').value = query.dataset; $('from').value = query.from || ''; $('to').value = query.to || '';
    if (![...$('limit').options].some(option => Number(option.value) === query.limit)) { const option = el('option', String(query.limit)); option.value = query.limit; $('limit').append(option); }
    $('limit').value = query.limit;
    $('dataset-note').textContent = Array.isArray(dataset().notes) ? dataset().notes.join(' ') : dataset().notes || '';
    renderFilters(); renderColumns(); renderGrouping(); renderSort();
  }
  function encoded(spec) { return encodeURIComponent(JSON.stringify(spec)); }
  function reportLink(spec) { return new URL(`/reports/builder?q=${encoded(spec)}`, window.location.origin).href; }
  function formatted(value, type) {
    if (value === null || value === undefined) return '—';
    if (type === 'number' && Number.isFinite(Number(value))) return new Intl.NumberFormat(undefined, {maximumFractionDigits: 4}).format(Number(value));
    if (type === 'boolean') return value === true || value === 'true' ? 'Yes' : 'No';
    return String(value);
  }
  function formattedCell(value, column) {
    if (value != null && !column.key.startsWith('count_') && /(?:amount|balance|rate|value)/i.test(column.key) && Number.isFinite(Number(value))) return new Intl.NumberFormat(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(Number(value));
    return formatted(value, column.type);
  }
  function renderResult(data) {
    const head = $('table').querySelector('thead'), body = $('table').querySelector('tbody'); head.replaceChildren(); body.replaceChildren();
    const header = el('tr');
    const cellClass = column => `${column.type === 'number' ? 'rb-number ' : ''}${column.type === 'date' ? 'rb-date ' : ''}${['clientName', 'projectName', 'vendor'].includes(column.key) ? 'rb-name ' : ''}${column.key === 'description' ? 'rb-description' : ''}`;
    data.columns.forEach(column => { const cell = el('th', column.label, cellClass(column)); cell.scope = 'col'; header.append(cell); });
    if (executed.groupBy.length) { const cell = el('th', 'Records'); cell.scope = 'col'; header.append(cell); } head.append(header);
    data.rows.forEach(row => { const tr = el('tr'); data.columns.forEach(column => { const cell = el('td', formattedCell(row[column.key], column), cellClass(column)); if (column.key === 'status' && row[column.key]) { cell.textContent = ''; const status = String(row[column.key]); const tone = status === 'paid' ? 'success' : status === 'overdue' ? 'danger' : ['void', 'written_off'].includes(status) ? 'neutral' : 'warning'; cell.append(el('span', status === 'written_off' ? 'Written off' : status, `app-status app-status-${tone} capitalize`)); } tr.append(cell); }); if (executed.groupBy.length) { const cell = el('td'); cell.append(button('View records', () => drill(row))); tr.append(cell); } body.append(tr); });
    if (!data.rows.length) { const tr = el('tr'), cell = el('td', 'No records match these settings. Adjust the dates or filters and run again.', 'rb-empty'); cell.colSpan = Math.max(1, data.columns.length); tr.append(cell); body.append(tr); }
    const start = data.rows.length ? Number(executed.offset) + 1 : 0;
    const count = `${new Intl.NumberFormat().format(data.totalRows)} ${executed.groupBy.length || executed.metrics.length ? 'summary rows' : 'records'}`;
    $('result-count').textContent = `${count} match this report. Exports include all matching rows.`;
    $('page-count').textContent = `${start}–${Number(executed.offset) + data.rows.length} of ${data.totalRows}`;
    const describe = group => [...(group.filters || []).map(filter => `${fieldLabel(filter.field)} ${operators[filter.operator] || filter.operator}${['is_null','is_not_null'].includes(filter.operator) ? '' : ` ${filter.values.join(', ')}`}`), ...(group.groups || []).map(child => `(${describe(child)})`)].join(group.match === 'any' ? ' OR ' : ' AND ');
    $('applied').textContent = `${dataset().label} · ${executed.from || 'All dates'} through ${executed.to || 'today'}${describe(executed.where) ? ` · ${describe(executed.where)}` : ''}`;
    $('notes').replaceChildren(); const notes = Array.isArray(data.notes) ? data.notes : data.notes ? [data.notes] : [];
    notes.forEach(note => $('notes').append(el('p', typeof note === 'string' ? note : JSON.stringify(note))));
    $('totals').replaceChildren();
    function totalLabel(key) { const metric = executed.metrics.find(item => `${item.function}_${item.field}` === key); return metric ? `${functions[metric.function]} of ${fieldLabel(metric.field)}` : fieldLabel(key); }
    function total(title, amount, key) { const node = el('div', title, 'rb-total'); node.append(el('strong', formattedCell(amount, {key, type: 'number'}))); $('totals').append(node); }
    for (const [key, amount] of Object.entries(data.totals?.numeric || {})) total(totalLabel(key), amount, key);
    for (const [currency, amounts] of Object.entries(data.totals?.currencies || {})) for (const [key, amount] of Object.entries(amounts)) total(`${totalLabel(key)} (${currency})`, amount, key);
  }
  function drill(row) {
    const depth = group => Math.max(0, ...(group.groups || []).map(child => 1 + depth(child)));
    if (executed.where.match === 'any' && depth(executed.where) >= 3) {
      error('This report already uses the maximum filter nesting. To view its underlying records, remove the grouping in Group and summarize; keep the existing filters, then add the row’s grouping values as conditions.');
      return;
    }
    summaryQuery = clone(executed);
    const filters = executed.groupBy.map(field => ({field, operator: row[field] == null ? 'is_null' : 'eq', values: row[field] == null ? [] : [String(row[field])]}));
    const spec = clone(executed); spec.where = spec.where.match === 'all' ? {...spec.where, filters: [...spec.where.filters, ...filters]} : {match: 'all', filters, groups: [clone(spec.where)]};
    spec.groupBy = []; spec.metrics = []; spec.sort = []; spec.columns = [...dataset().defaultColumns]; spec.offset = 0;
    query = spec; hydrate(); changed(); $('back-summary').hidden = false; run(clone(spec));
  }
  async function run(spec) {
    if (busy) return;
    if (spec.from && spec.to && spec.from > spec.to) { error('The From date must be on or before the Through date.'); return; }
    if (!spec.columns.length && !spec.metrics.length && !spec.groupBy.length) { error('Select at least one column or calculation.'); return; }
    busy = true; error(''); updateActions(); $('change-status').textContent = 'Running report…';
    // Keep edits made during a request separate from the executed result.
    const submitted = clone(spec), before = JSON.stringify(query);
    try {
      const response = await fetch(`/reports/query?q=${encoded(submitted)}`, {headers: {Accept: 'application/json'}});
      const text = await response.text(); let data;
      try { data = JSON.parse(text); } catch (_) { throw new Error(response.status === 401 || response.redirected ? 'Your session has expired. Sign in and run the report again.' : 'The report could not be loaded. Please try again.'); }
      if (!response.ok) throw new Error(data.message || data.error || 'Check your report settings and try again.');
      executed = clone(data.query || submitted); result = data;
      dirty = JSON.stringify(query) !== before;
      if (!dirty) { query = clone(executed); hydrate(); }
      renderResult(data);
      if (!dirty) history.replaceState(null, '', reportLink(executed));
      $('change-status').textContent = dirty ? 'Settings changed. Run report to update results and exports.' : 'Report is up to date.';
    } catch (failure) { error(failure.message); $('change-status').textContent = 'Report was not updated. Check the message and try again.'; }
    finally { busy = false; updateActions(); }
  }
  try {
    catalog = JSON.parse(root.dataset.catalog); query = JSON.parse(root.dataset.query || 'null');
    if (!Array.isArray(catalog) || !catalog.length) throw new Error('No report datasets are available.');
    if (!query) query = fresh(catalog[0].key);
    if (!dataset()) throw new Error('This report uses an unavailable dataset. Open the report builder from Reports to start again.');
    for (const item of catalog) {
      const option = el('option', item.label); option.value = item.key; $('dataset').append(option);
    }
    buildPresets();
    buildPeriods();
    $('dataset').addEventListener('change', () => { query = fresh($('dataset').value); hydrate(); changed(); });
    $('preset').addEventListener('change', () => { if (presets.has($('preset').value)) { choosePreset($('preset').value); run(clone(query)); } });
    $('back-summary').addEventListener('click', () => { if (!summaryQuery) return; query = clone(summaryQuery); summaryQuery = null; $('back-summary').hidden = true; hydrate(); changed(); run(clone(query)); });
    for (const key of ['from', 'to']) $(key).addEventListener('input', () => { query[key] = $(key).value || null; changed(); });
    $('limit').addEventListener('change', () => { query.limit = Number($('limit').value); changed(); });
    $('add-metric').addEventListener('click', () => { const field = fields().find(item => item.key === 'amount' || item.key === 'hours') || fields().find(item => item.numeric || item.type === 'number'); query.metrics.push({field: (field || fields()[0]).key, function: field ? 'sum' : 'count'}); query.columns = [...query.groupBy]; renderColumns(); renderGrouping(); renderSort(); changed(); });
    $('add-sort').addEventListener('click', () => { query.sort.push({field: query.groupBy[0] || (query.metrics[0] ? `${query.metrics[0].function}_${query.metrics[0].field}` : fields()[0].key), direction: 'asc'}); renderSort(); changed(); });
    $('form').addEventListener('submit', event => { event.preventDefault(); const spec = clone(query); if (dirty) spec.offset = 0; run(spec); });
    $('previous').addEventListener('click', () => run({...clone(executed), offset: Math.max(0, executed.offset - executed.limit)}));
    $('next').addEventListener('click', () => run({...clone(executed), offset: result.nextOffset}));
    for (const format of ['csv', 'xlsx']) $(format).addEventListener('click', () => { if (!dirty && executed) window.location.assign(`/reports/export?q=${encoded(executed)}&format=${format}`); });
    $('copy').addEventListener('click', async () => {
      if (dirty || !executed) return;
      try { await navigator.clipboard.writeText(reportLink(executed)); $('change-status').textContent = 'Report link copied.'; }
      catch (_) { error('Copy the report link from your browser’s address bar.'); }
    });
    const isSummary = query.groupBy?.length || query.metrics?.length;
    const hasConditions = group => !!((group?.filters || []).length || (group?.groups || []).some(hasConditions));
    const custom = isSummary || hasConditions(query.where) || (query.sort || []).length || JSON.stringify(query.columns) !== JSON.stringify(dataset().defaultColumns);
    if (custom) {
      const title = `${dataset().label} ${isSummary ? 'summary' : 'report'}`;
      const option = el('option', `${title} (custom)`); option.value = 'custom'; $('preset').append(option); $('preset').value = 'custom';
      $('report-title').textContent = title;
      $('report-description').textContent = 'This linked report’s dates, filters, columns, and calculations are loaded below.';
    } else {
      const defaultPreset = [...$('preset').options].find(option => option.value === query.dataset);
      $('report-title').textContent = defaultPreset?.textContent || `${dataset().label} report`;
      $('preset').value = query.dataset;
      for (const node of root.querySelectorAll('[data-preset]')) if (node.dataset.preset === query.dataset) node.classList.add('is-selected');
    }
    hydrate(); run(clone(query));
  } catch (failure) { error(failure.message); $('run').disabled = true; }
})();
