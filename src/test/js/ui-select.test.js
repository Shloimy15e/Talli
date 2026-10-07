const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/ui/select.js'),
  'utf8'
);
const documentStub = { addEventListener() {} };
const windowStub = { addEventListener() {} };

vm.runInNewContext(source, {
  document: documentStub,
  MutationObserver: class MutationObserver {},
  window: windowStub
});

const picker = windowStub.TalliSelectTest;
const options = [
  { index: 0, label: 'Blank message', primary: 'Blank message', secondary: '', disabled: false },
  { index: 1, label: 'Disabled billing', primary: 'Disabled billing', secondary: '', disabled: true },
  { index: 2, label: 'Monthly billing', primary: 'Monthly billing', secondary: '', disabled: false },
  { index: 3, label: 'Payment reminder', primary: 'Payment reminder', secondary: '', disabled: false },
  { index: 4, label: 'Dynamiq Solutions <billing@dynamiq.dev>', primary: 'Dynamiq Solutions', secondary: 'billing@dynamiq.dev', disabled: false }
];

test('website single selects enhance automatically while standalone controls require opt-in', () => {
  const select = {
    multiple: false,
    size: 0,
    hasAttribute: () => false,
    closest: () => ({})
  };
  assert.equal(picker.isEligible(select), true);
  assert.equal(picker.isEligible({ ...select, closest: () => null }), false);
  assert.equal(picker.isEligible({ ...select, closest: () => null, hasAttribute: (name) => name === 'data-ui-select' }), true);
});

test('native opt-out and multi-selection listboxes retain their native behavior', () => {
  const select = { multiple: false, size: 0, hasAttribute: () => false, closest: () => ({}) };
  assert.equal(picker.isEligible({ ...select, multiple: true }), false);
  assert.equal(picker.isEligible({ ...select, size: 4 }), false);
  assert.equal(picker.isEligible({ ...select, hasAttribute: (name) => name === 'data-ui-select-native' }), false);
});

test('sender labels expose the address as distinct secondary text', () => {
  assert.deepEqual(
    { ...picker.splitLabel('Dynamiq Solutions <billing@dynamiq.dev>') },
    { primary: 'Dynamiq Solutions', secondary: 'billing@dynamiq.dev' }
  );
});

test('arrow navigation skips disabled options and clamps at the list edges', () => {
  assert.equal(picker.nextEnabledIndex(options, 0, 1), 2);
  assert.equal(picker.nextEnabledIndex(options, 2, -1), 0);
  assert.equal(picker.nextEnabledIndex(options, 4, 1), 4);
  assert.equal(picker.edgeEnabledIndex(options, true), 4);
});

test('typeahead searches after the active option, cycles repeated keys, and wraps', () => {
  assert.equal(picker.typeaheadIndex(options, 'pay', 0), 3);
  assert.equal(picker.typeaheadIndex(options, 'blank', 3), 0);
  assert.equal(picker.typeaheadIndex(options, 'mm', 0), 2);
  assert.equal(picker.typeaheadIndex(options, 'bill', 3), 4);
  assert.equal(picker.typeaheadIndex(options, 'disabled', 0), 0);
});

test('search filters name and address while preserving original select indexes', () => {
  assert.deepEqual(
    picker.filterOptions(options, 'billing').map((option) => option.index),
    [1, 2, 4]
  );
  assert.deepEqual(
    picker.filterOptions(options, '@dynamiq').map((option) => option.index),
    [4]
  );
  assert.equal(picker.nextEnabledIndex(picker.filterOptions(options, 'billing'), 2, 1), 4);
});

test('popup placement stays inside the viewport and opens above when space is tight', () => {
  const below = picker.popupPlacement({ left: 20, bottom: 80, top: 40, width: 220 }, 320, 640, 288);
  assert.deepEqual({ ...below }, { left: 20, maxHeight: 288, top: 84, width: 220 });

  const above = picker.popupPlacement({ left: 290, bottom: 620, top: 580, width: 180 }, 320, 640, 288);
  assert.equal(above.left, 132);
  assert.equal(above.top, 288);
  assert.equal(above.maxHeight, 288);
});
