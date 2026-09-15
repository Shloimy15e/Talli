const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/mail-composer.js'),
  'utf8'
);
const documentStub = {
  addEventListener() {},
  getElementById(id) {
    return id === 'email-signature-styles'
      ? { textContent: '@media only screen and (max-width:480px) { .dynamiq-email-signature__contact { border-top:1px solid #ea7c28 !important; } }' }
      : null;
  },
  querySelectorAll() { return []; }
};
const windowStub = {
  addEventListener() {},
  location: { origin: 'https://app.example.test' },
  removeEventListener() {},
  setTimeout,
  clearTimeout
};

vm.runInNewContext(source, {
  CustomEvent: class CustomEvent {},
  DOMParser: class DOMParser {},
  URL,
  document: documentStub,
  localStorage: { getItem() { return null; }, removeItem() {}, setItem() {} },
  window: windowStub
});

const composer = windowStub.TalliMailComposerTest;

test('draft keys separate actors, new messages, and replies', () => {
  assert.equal(composer.draftKey('admin@example.test', ''), 'talli:mail-draft:v1:admin@example.test:new');
  assert.equal(composer.draftKey('admin@example.test', '42'), 'talli:mail-draft:v1:admin@example.test:42');
});

test('legacy reply envelope edits count as draft content without dirtying unchanged replies', () => {
  assert.equal(composer.replyEnvelopeChanged(false, 'old@example.test', 'Saved subject',
    'old@example.test', 'Saved subject'), false);
  assert.equal(composer.replyEnvelopeChanged(false, 'old@example.test', 'Saved subject',
    'new@example.test', 'Saved subject'), true);
  assert.equal(composer.replyEnvelopeChanged(false, 'old@example.test', 'Saved subject',
    'old@example.test', 'Edited subject'), true);
  assert.equal(composer.replyEnvelopeChanged(true, 'old@example.test', 'Re: Saved subject',
    'new@example.test', 'Edited subject'), false);
});

test('compose wraps one in-body signature without appending another copy', () => {
  const editorHtml = '<p>Hello $&</p><div data-signature="1"><strong>Team</strong></div>';
  const result = composer.composeBody(editorHtml, '<main>{{body}}</main>');
  assert.equal(result, '<main><p>Hello $&</p><div data-signature="1"><strong>Team</strong></div></main>');
  assert.equal(result.match(/data-signature=/g).length, 1);
});

test('preview metadata combines selected and manual copy recipients', () => {
  assert.equal(
    composer.previewRecipients(['Client One · one@example.test'], 'two@example.test; three@example.test'),
    'Client One · one@example.test, two@example.test, three@example.test'
  );
  assert.equal(composer.previewRecipients([], ''), '');
});

test('untemplated email defaults to Arial without changing explicit formatting', () => {
  const content = '<p>Hello</p><span style="font-family:Georgia">Custom</span>';
  const result = composer.composeBody(content, '');
  assert.match(result, /@media only screen and \(max-width:480px\)/);
  assert.match(result, /\.dynamiq-email-signature__contact/);
  assert.ok(result.endsWith(
    '<div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.65">' + content + '</div>'));
});

test('only reserved signature classes survive on their intended elements', () => {
  assert.equal(composer.safeSignatureClasses('TABLE', 'message dynamiq-email-signature'),
    'dynamiq-email-signature');
  assert.equal(composer.safeSignatureClasses('DIV', 'dynamiq-email-signature__contact arbitrary'),
    'dynamiq-email-signature__contact');
  assert.equal(composer.safeSignatureClasses('P', 'dynamiq-email-signature__contact'), '');
  assert.equal(composer.safeSignatureClasses('DIV', 'message arbitrary'), '');
});

test('attachment validation enforces per-file and aggregate limits', () => {
  assert.match(composer.attachmentIssue([{ name: 'large.pdf', size: 21 }], 20, 30), /large\.pdf is too large/);
  assert.match(composer.attachmentIssue([{ name: 'a', size: 16 }, { name: 'b', size: 16 }], 20, 30), /total limit/);
  assert.equal(composer.attachmentIssue([{ name: 'a', size: 10 }], 20, 30), '');
});

test('inserted URLs reject script protocols', () => {
  assert.equal(composer.safeUrl('javascript:alert(1)', true), '');
  assert.equal(composer.safeUrl('mailto:billing@example.test', true), 'mailto:billing@example.test');
  assert.equal(composer.safeUrl('tel:+15551234567', true), 'tel:+15551234567');
  assert.equal(composer.safeUrl('https://example.test/image.png', false), 'https://example.test/image.png');
  assert.equal(composer.safeImageUrl('data:image/svg+xml;base64,PHN2Zz4=', false), '');
  assert.match(composer.safeImageUrl('data:image/png;base64,iVBORw0KGgo='), /^data:image\/png/);
});

test('signature styles preserve layout values but reject executable CSS', () => {
  assert.equal(composer.safeStyleValue('width', '460px'), true);
  assert.equal(composer.safeStyleValue('display', 'inline-block'), true);
  assert.equal(composer.safeStyleValue('border-left', '2px solid #ea7c28'), true);
  assert.equal(composer.safeStyleValue('border-left-color', 'rgb(234, 124, 40)'), true);
  assert.equal(composer.safeStyleValue('text-decoration-line', 'none'), true);
  assert.equal(composer.safeStyleValue('text-wrap-mode', 'nowrap'), true);
  assert.equal(composer.safeStyleValue('white-space-collapse', 'collapse'), true);
  assert.equal(composer.safeStyleValue('text-wrap-mode', 'balance'), false);
  assert.equal(composer.safeStyleValue('display', 'position'), false);
  assert.equal(composer.safeStyleValue('background', 'url(javascript:alert(1))'), false);
  assert.equal(composer.safeStyleValue('width', 'expression(alert(1))'), false);
});
