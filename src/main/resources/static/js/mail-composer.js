(function () {
  'use strict';

  const DRAFT_PREFIX = 'talli:mail-draft:v1:';
  let inlineReturnFocus = null;
  const DRAFT_DELAY_MS = 500;
  const SIGNATURE_CLASS_TAGS = new Map([
    ['dynamiq-email-signature', 'TABLE'],
    ['dynamiq-email-signature__layout', 'TD'],
    ['dynamiq-email-signature__logo', 'DIV'],
    ['dynamiq-email-signature__spacer', 'SPAN'],
    ['dynamiq-email-signature__contact', 'DIV']
  ]);

  function draftKey(actor, replyToEmailId) {
    return `${DRAFT_PREFIX}${actor || 'anonymous'}:${replyToEmailId || 'new'}`;
  }

  function replyEnvelopeChanged(providerThreaded, initialTo, initialSubject, toAddress, subject) {
    if (providerThreaded) return false;
    return toAddress !== initialTo || subject !== initialSubject;
  }

  function composeBody(html, templateHtml) {
    const body = html || '';
    const signatureStyles = document.getElementById('email-signature-styles')?.textContent?.trim() || '';
    return templateHtml
      ? templateHtml.replace('{{body}}', () => body)
      : `${signatureStyles ? `<style>${signatureStyles}</style>` : ''}<div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.65">${body}</div>`;
  }

  function safeSignatureClasses(tagName, value) {
    return String(value || '').split(/\s+/)
      .filter((className) => SIGNATURE_CLASS_TAGS.get(className) === tagName)
      .join(' ');
  }

  function previewRecipients(selectedRecipients, manualRecipients) {
    const selected = Array.from(selectedRecipients || [])
      .map((value) => String(value || '').trim())
      .filter(Boolean);
    const manual = String(manualRecipients || '').split(/[,;\s]+/).map((value) => value.trim()).filter(Boolean);
    return [...selected, ...manual].join(', ');
  }

  function formatFileSize(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 MB';
    return `${Math.round(bytes / (1024 * 1024))} MB`;
  }

  function attachmentIssue(files, maxFileBytes, maxTotalBytes) {
    const selected = Array.from(files || []);
    const oversized = selected.find((file) => file.size > maxFileBytes);
    if (oversized) {
      return `${oversized.name} is too large. Each attachment must be ${formatFileSize(maxFileBytes)} or smaller.`;
    }
    const totalBytes = selected.reduce((total, file) => total + file.size, 0);
    if (totalBytes > maxTotalBytes) {
      return `The selected attachments exceed the ${formatFileSize(maxTotalBytes)} total limit. Remove a file or choose smaller files.`;
    }
    return '';
  }

  function safeUrl(value, allowContactLinks) {
    const input = String(value || '').trim();
    if (!input) return '';
    try {
      const url = new URL(input, window.location.origin);
      const allowed = ['http:', 'https:'];
      if (allowContactLinks) allowed.push('mailto:', 'tel:');
      return allowed.includes(url.protocol) ? input : '';
    } catch (_) {
      return '';
    }
  }

  function safeImageUrl(value) {
    const input = String(value || '').trim();
    if (/^data:image\/(?:gif|jpe?g|png|webp);base64,[a-z0-9+/=\s]+$/i.test(input)) return input;
    return safeUrl(input, false);
  }

  function safeStyleValue(property, value) {
    const normalized = String(value || '').toLowerCase();
    if (/url\s*\(|expression\s*\(|javascript:|vbscript:|behavior\s*:|@import/i.test(normalized)) return false;
    if (property === 'display') {
      return ['block', 'inline', 'inline-block', 'table', 'table-row', 'table-cell'].includes(normalized.trim());
    }
    if (property === 'text-wrap-mode') return ['wrap', 'nowrap'].includes(normalized.trim());
    if (property === 'white-space-collapse') {
      return ['collapse', 'preserve', 'preserve-breaks', 'break-spaces'].includes(normalized.trim());
    }
    return true;
  }

  function sanitizePastedHtml(rawHtml) {
    const parser = new DOMParser();
    const documentNode = parser.parseFromString(rawHtml || '', 'text/html');
    const allowedTags = new Set([
      'A', 'B', 'BLOCKQUOTE', 'BR', 'CODE', 'DIV', 'EM', 'H1', 'H2', 'H3', 'HR',
      'I', 'IMG', 'LI', 'OL', 'P', 'PRE', 'S', 'SPAN', 'STRIKE', 'STRONG', 'TABLE',
      'TBODY', 'TD', 'TFOOT', 'TH', 'THEAD', 'TR', 'U', 'UL'
    ]);
    const allowedStyles = new Set([
      'background', 'background-color', 'border', 'border-bottom', 'border-bottom-color',
      'border-bottom-style', 'border-bottom-width', 'border-collapse', 'border-color',
      'border-left', 'border-left-color', 'border-left-style', 'border-left-width',
      'border-radius', 'border-right', 'border-right-color', 'border-right-style',
      'border-right-width', 'border-spacing', 'border-style', 'border-top',
      'border-top-color', 'border-top-style', 'border-top-width', 'border-width',
      'box-sizing', 'color', 'display', 'font', 'font-family', 'font-size', 'font-style',
      'font-variant', 'font-weight', 'height', 'letter-spacing',
      'line-height', 'margin', 'margin-bottom', 'margin-left', 'margin-right', 'margin-top',
      'max-height', 'max-width', 'min-height', 'min-width', 'padding', 'padding-bottom',
      'padding-left', 'padding-right', 'padding-top', 'text-align', 'text-decoration',
      'text-decoration-color', 'text-decoration-line', 'text-decoration-style',
      'text-decoration-thickness', 'text-underline-offset', 'vertical-align',
      'text-wrap-mode', 'white-space', 'white-space-collapse', 'width', 'word-break'
    ]);
    const tableAttributes = new Set([
      'align', 'bgcolor', 'border', 'cellpadding', 'cellspacing', 'colspan', 'height',
      'role', 'rowspan', 'valign', 'width'
    ]);

    Array.from(documentNode.body.querySelectorAll('*')).forEach((element) => {
      if (!allowedTags.has(element.tagName)) {
        element.replaceWith(...element.childNodes);
        return;
      }

      Array.from(element.attributes).forEach((attribute) => {
        if (attribute.name === 'class') {
          const safeClasses = safeSignatureClasses(element.tagName, attribute.value);
          if (safeClasses) element.setAttribute('class', safeClasses);
          else element.removeAttribute('class');
          return;
        }

        if (attribute.name === 'style') {
          const safeStyles = Array.from(element.style)
            .filter((property) => allowedStyles.has(property)
              && safeStyleValue(property, element.style.getPropertyValue(property)))
            .map((property) => {
              const priority = element.style.getPropertyPriority(property) ? ' !important' : '';
              return `${property}: ${element.style.getPropertyValue(property)}${priority}`;
            })
            .join('; ');
          if (safeStyles) element.setAttribute('style', safeStyles);
          else element.removeAttribute('style');
          return;
        }

        if (element.tagName === 'A' && ['href', 'title'].includes(attribute.name)) return;
        if (element.tagName === 'IMG' && ['src', 'alt', 'title', 'width', 'height'].includes(attribute.name)) return;
        if (['TABLE', 'TBODY', 'TD', 'TFOOT', 'TH', 'THEAD', 'TR'].includes(element.tagName)
            && tableAttributes.has(attribute.name)) return;
        if (element.tagName === 'DIV'
            && ['data-signature', 'data-signature-spacer'].includes(attribute.name)
            && attribute.value === '1') return;
        element.removeAttribute(attribute.name);
      });

      if (element.tagName === 'A') {
        const href = safeUrl(element.getAttribute('href'), true);
        if (href) {
          element.setAttribute('href', href);
          element.setAttribute('rel', 'noopener noreferrer');
          element.setAttribute('target', '_blank');
        } else {
          element.removeAttribute('href');
        }
      } else if (element.tagName === 'IMG') {
        const src = safeImageUrl(element.getAttribute('src'));
        if (src) element.setAttribute('src', src);
        else element.remove();
      }
    });

    return documentNode.body.innerHTML;
  }

  function consumeSentDraftKeys(root) {
    const scope = root && root.querySelectorAll ? root : document;
    scope.querySelectorAll('meta[name="talli-sent-draft-key"], [data-sent-draft-key][data-key]').forEach((marker) => {
      try {
        const key = marker.content || marker.dataset.key;
        if (key) localStorage.removeItem(key);
      } catch (_) {
        // Storage can be disabled by browser privacy settings.
      }
      marker.remove();
    });
  }

  window.mailComposer = function mailComposer(root) {
    return {
      actor: root.dataset.actor || 'anonymous',
      replyToEmailId: root.dataset.replyId || '',
      providerThreadedReply: root.dataset.providerThreaded === 'true',
      clientId: root.dataset.client || '',
      senderEmail: root.dataset.sender || '',
      signature: root.dataset.signature || '',
      defaultSignature: root.dataset.signature || '',
      signatureFromDraft: false,
      includeSignature: (root.dataset.signature || '').trim().length > 0,
      toAddress: root.dataset.toAddress || '',
      subject: root.dataset.subject || '',
      ccManual: root.dataset.cc || '',
      bccManual: root.dataset.bcc || '',
      ccOpen: Boolean((root.dataset.cc || '').trim()),
      bccOpen: Boolean((root.dataset.bcc || '').trim()),
      optionsOpen: false,
      formattingOpen: false,
      previewOpen: false,
      previewCloseTimer: null,
      previewViewport: 'desktop',
      previewRange: null,
      previewSourceSelection: null,
      minimized: false,
      expanded: false,
      inlineMode: false,
      mode: 'rich',
      html: '',
      plain: '',
      templateId: '',
      templateHtml: '',
      senderLoading: false,
      senderRequest: 0,
      senderError: '',
      attachmentNames: [],
      attachmentError: '',
      filesNeedReattach: false,
      draftRestored: false,
      draftStatus: '',
      isSending: false,
      draftTimer: null,
      beforeUnloadHandler: null,
      returnFocusTarget: null,

      init() {
        this.inlineMode = Boolean(root.closest('#reply-composer'));
        this.returnFocusTarget = this.inlineMode ? inlineReturnFocus : null;
        this.restoreDraft();
        this.$nextTick(async () => {
          const senderOption = this.$refs.sender.selectedOptions[0];
          this.defaultSignature = sanitizePastedHtml(senderOption ? senderOption.dataset.signature || '' : this.defaultSignature);
          this.signature = sanitizePastedHtml(this.signature);
          this.html = sanitizePastedHtml(this.html);
          this.$refs.editor.innerHTML = this.html;
          const restoredSignature = this.signatureNode();
          if (restoredSignature) {
            this.signature = restoredSignature.innerHTML;
            this.includeSignature = true;
          } else if (this.includeSignature && this.signature) {
            if (!this.signatureFromDraft) this.signature = this.defaultSignature;
            this.insertSignature(this.signature);
          } else {
            this.includeSignature = false;
          }
          this.syncFromRich(false);
          if (this.inlineMode) this.$refs.editor.focus();
          else if (!this.draftRestored) this.$refs.toAddress.focus();
          if (senderOption && this.draftRestored) await this.changeSender(senderOption, false, true);
          root.querySelectorAll('[data-ui-select]').forEach((select) => {
            select.dispatchEvent(new CustomEvent('ui-select:refresh'));
          });
        });
        this.beforeUnloadHandler = () => this.saveDraft(true);
        window.addEventListener('beforeunload', this.beforeUnloadHandler);
      },

      destroy() {
        if (this.beforeUnloadHandler) window.removeEventListener('beforeunload', this.beforeUnloadHandler);
        if (this.draftTimer) window.clearTimeout(this.draftTimer);
        window.TalliSelect?.cleanup(root);
      },

      get storageKey() {
        return draftKey(this.actor, this.replyToEmailId);
      },

      compose() {
        return composeBody(this.html, this.templateHtml);
      },

      senderSummary() {
        const option = this.$refs.sender?.selectedOptions?.[0];
        return option ? option.textContent.trim() : this.senderEmail;
      },

      recipientSummary(fieldName, manualRecipients) {
        const selected = Array.from(root.querySelectorAll(`input[name="${fieldName}"]:checked`))
          .map((input) => input.closest('label')?.textContent?.trim() || input.value);
        return previewRecipients(selected, manualRecipients);
      },

      capturePreviewSelection() {
        this.previewRange = null;
        this.previewSourceSelection = null;
        if (this.mode === 'source') {
          this.previewSourceSelection = {
            start: this.$refs.source.selectionStart,
            end: this.$refs.source.selectionEnd
          };
          return;
        }

        const selection = window.getSelection();
        if (selection && selection.rangeCount) {
          const range = selection.getRangeAt(0);
          if (this.$refs.editor.contains(range.commonAncestorContainer)) this.previewRange = range.cloneRange();
        }
      },

      openPreview() {
        this.capturePreviewSelection();
        if (this.mode === 'source') {
          this.html = sanitizePastedHtml(this.html);
          this.$refs.editor.innerHTML = this.html;
        }
        this.syncFromRich(false);
        this.previewOpen = true;
        this.$nextTick(() => {
          if (!this.$refs.previewDialog.open) this.$refs.previewDialog.showModal();
          this.$refs.previewBack.focus();
        });
      },

      closePreview() {
        const dialog = this.$refs.previewDialog;
        if (!dialog?.open) {
          this.previewClosed();
          return;
        }
        if (dialog.dataset.closing) return;

        const finishClose = () => {
          this.previewCloseTimer = null;
          if (dialog.open) dialog.close();
        };
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
          finishClose();
          return;
        }

        dialog.dataset.closing = 'true';
        this.previewCloseTimer = window.setTimeout(finishClose, 200);
      },

      previewClosed() {
        window.clearTimeout(this.previewCloseTimer);
        this.previewCloseTimer = null;
        delete this.$refs.previewDialog?.dataset.closing;
        this.previewOpen = false;
        this.$nextTick(() => {
          window.requestAnimationFrame(() => {
            if (this.mode === 'source') {
              this.$refs.source.focus({ preventScroll: true });
              if (this.previewSourceSelection) {
                this.$refs.source.setSelectionRange(this.previewSourceSelection.start, this.previewSourceSelection.end);
              }
              return;
            }

            this.$refs.editor.focus({ preventScroll: true });
            if (this.previewRange) {
              const selection = window.getSelection();
              selection.removeAllRanges();
              selection.addRange(this.previewRange);
            }
          });
        });
      },

      async changeSender(option, save = true, preserveExistingSignature = false) {
        this.senderLoading = true;
        this.senderError = '';
        const address = this.senderEmail;
        const request = ++this.senderRequest;
        if (this.mode === 'source') {
          this.html = sanitizePastedHtml(this.html);
          this.$refs.editor.innerHTML = this.html;
        }

        const existingSignature = this.signatureNode();
        if (existingSignature) this.signature = existingSignature.innerHTML;
        const nextDefaultSignature = sanitizePastedHtml(option ? option.dataset.signature || '' : '');
        this.defaultSignature = nextDefaultSignature;
        if (!nextDefaultSignature) {
          this.removeSignature(false);
          this.signature = '';
          this.includeSignature = false;
        } else if (preserveExistingSignature && existingSignature) {
          this.signature = existingSignature.innerHTML;
          this.includeSignature = true;
        } else if (preserveExistingSignature) {
          if (this.includeSignature && this.signature) this.insertSignature(this.signature);
        } else {
          this.signature = nextDefaultSignature;
          if (this.includeSignature) this.insertSignature(this.signature);
        }
        this.syncFromRich(false);

        try {
          const response = await fetch(`/emails/sender-templates?senderEmail=${encodeURIComponent(address)}`);
          if (!response.ok) throw new Error('Unable to load this sender. Select it again to retry.');
          const templates = await response.json();
          if (this.senderRequest !== request) return;
          Array.from(this.$refs.templates.options).forEach((item) => {
            const template = templates.find((candidate) => String(candidate.id) === String(item.value));
            item.dataset.html = template ? template.html || '' : '';
          });
          const selected = this.$refs.templates.selectedOptions[0];
          this.applyTemplate(selected ? selected.dataset.html : '', false);
        } catch (error) {
          if (this.senderRequest === request) {
            this.senderError = error instanceof Error ? error.message : 'Unable to load this sender.';
          }
        } finally {
          if (this.senderRequest === request) this.senderLoading = false;
        }
        if (save) this.scheduleDraft();
      },

      changeClient(event) {
        const selected = event.target.selectedOptions[0];
        if (selected && selected.dataset.email && !this.replyToEmailId) {
          this.toAddress = selected.dataset.email;
        }
        this.scheduleDraft();
      },

      exec(command, value) {
        this.$refs.editor.focus();
        document.execCommand(command, false, value || null);
        this.syncFromRich();
      },

      makeLink() {
        const requested = window.prompt('Link URL', 'https://');
        if (requested === null) return;
        const url = safeUrl(requested, true);
        if (url) this.exec('createLink', url);
      },

      insertImage() {
        const requested = window.prompt('Image URL', 'https://');
        if (requested === null) return;
        const url = safeUrl(requested, false);
        if (url) this.exec('insertImage', url);
      },

      setBlock(tag) {
        if (tag) this.exec('formatBlock', tag);
      },

      setColor(color) {
        if (color) this.exec('foreColor', color);
      },

      setFont(face) {
        if (face) this.exec('fontName', face);
      },

      handlePaste(event) {
        const html = event.clipboardData && event.clipboardData.getData('text/html');
        if (!html) return;
        event.preventDefault();
        document.execCommand('insertHTML', false, sanitizePastedHtml(html));
        this.syncFromRich();
      },

      syncFromRich(save = true) {
        if (!this.$refs.editor) return;
        const signature = this.signatureNode();
        if (signature) {
          this.signature = signature.innerHTML;
          this.includeSignature = true;
        } else if (this.includeSignature) {
          this.includeSignature = false;
          this.$refs.editor.querySelectorAll('[data-signature-spacer="1"]').forEach((spacer) => spacer.remove());
        }
        this.html = this.$refs.editor.innerHTML;
        this.plain = this.$refs.editor.innerText;
        if (save) this.scheduleDraft();
      },

      signatureNode() {
        return this.$refs.editor ? this.$refs.editor.querySelector('[data-signature="1"]') : null;
      },

      insertSignature(signatureHtml) {
        if (!this.$refs.editor || !signatureHtml) return;
        const safeSignature = sanitizePastedHtml(signatureHtml);
        let signature = this.signatureNode();
        if (signature) {
          signature.innerHTML = safeSignature;
          return;
        }

        if (!this.$refs.editor.textContent.trim() && !this.$refs.editor.querySelector('img')) {
          const writingLine = document.createElement('div');
          writingLine.appendChild(document.createElement('br'));
          this.$refs.editor.appendChild(writingLine);
        }
        const spacer = document.createElement('div');
        spacer.setAttribute('data-signature-spacer', '1');
        spacer.appendChild(document.createElement('br'));
        this.$refs.editor.appendChild(spacer);

        signature = document.createElement('div');
        signature.setAttribute('data-signature', '1');
        signature.innerHTML = safeSignature;
        this.$refs.editor.appendChild(signature);
      },

      removeSignature(preserveEdited = true) {
        const signature = this.signatureNode();
        if (signature && preserveEdited) this.signature = signature.innerHTML;
        if (signature) signature.remove();
        this.$refs.editor?.querySelectorAll('[data-signature-spacer="1"]').forEach((spacer) => spacer.remove());
      },

      toggleSignature() {
        if (this.includeSignature) {
          this.removeSignature(true);
          this.includeSignature = false;
        } else if (this.signature) {
          this.includeSignature = true;
          this.insertSignature(this.signature);
        }
        this.syncFromRich(false);
        this.scheduleDraft();
      },

      toggleMode() {
        if (this.mode === 'rich') {
          this.syncFromRich(false);
          this.mode = 'source';
        } else {
          this.mode = 'rich';
          this.$nextTick(() => {
            this.html = sanitizePastedHtml(this.html);
            this.$refs.editor.innerHTML = this.html;
            const signature = this.signatureNode();
            if (signature) {
              this.signature = signature.innerHTML;
              this.includeSignature = true;
            } else if (this.includeSignature && this.signature) {
              this.insertSignature(this.signature);
            }
            this.syncFromRich(false);
          });
        }
        this.scheduleDraft();
      },

      applyTemplate(html, save = true) {
        this.templateHtml = html || '';
        if (save) this.scheduleDraft();
      },

      validateAttachments(event) {
        const input = event.target;
        const files = Array.from(input.files || []);
        this.attachmentError = attachmentIssue(
          files,
          Number(input.dataset.maxFileBytes),
          Number(input.dataset.maxTotalBytes)
        );
        if (this.attachmentError) {
          input.value = '';
          this.attachmentNames = [];
        } else {
          this.attachmentNames = files.map((file) => ({ name: file.name, size: file.size }));
          this.filesNeedReattach = false;
        }
        this.$nextTick(() => {
          if (typeof window.initLucide === 'function') window.initLucide();
        });
        this.scheduleDraft();
      },

      removeAttachment(index) {
        const input = this.$refs.attachments;
        const files = Array.from(input.files || []);
        if (typeof DataTransfer !== 'undefined') {
          const transfer = new DataTransfer();
          files.forEach((file, fileIndex) => {
            if (fileIndex !== index) transfer.items.add(file);
          });
          input.files = transfer.files;
          this.validateAttachments({ target: input });
        } else {
          input.value = '';
          this.attachmentNames = [];
          this.attachmentError = '';
          this.scheduleDraft();
        }
      },

      scheduleDraft() {
        this.draftStatus = 'Saving…';
        if (this.draftTimer) window.clearTimeout(this.draftTimer);
        this.draftTimer = window.setTimeout(() => this.saveDraft(false), DRAFT_DELAY_MS);
      },

      draftPayload() {
        return {
          version: 1,
          savedAt: new Date().toISOString(),
          toAddress: this.toAddress,
          subject: this.subject,
          clientId: this.clientId,
          senderEmail: this.senderEmail,
          ccManual: this.ccManual,
          bccManual: this.bccManual,
          ccUserIds: this.checkedValues('ccUserId'),
          bccUserIds: this.checkedValues('bccUserId'),
          html: this.html,
          signatureHtml: this.signature,
          mode: this.mode,
          templateId: this.templateId,
          includeSignature: this.includeSignature,
          ccOpen: this.ccOpen,
          bccOpen: this.bccOpen,
          attachmentNames: this.attachmentNames.map((file) => file.name)
        };
      },

      checkedValues(name) {
        return Array.from(root.querySelectorAll(`input[name="${name}"]:checked`)).map((input) => input.value);
      },

      restoreCheckedValues(name, values) {
        const selected = new Set((values || []).map(String));
        root.querySelectorAll(`input[name="${name}"]`).forEach((input) => {
          input.checked = selected.has(String(input.value));
        });
      },

      saveDraft(silent) {
        if (this.isSending) return;
        try {
          if (this.mode === 'source') this.syncSignatureFromSource();
          if (!this.hasDraftContent()) {
            localStorage.removeItem(this.storageKey);
            if (!silent) this.draftStatus = '';
            return;
          }
          localStorage.setItem(this.storageKey, JSON.stringify(this.draftPayload()));
          if (!silent) this.draftStatus = 'Saved locally';
        } catch (_) {
          if (!silent) this.draftStatus = 'Local draft unavailable';
        }
      },

      hasDraftContent() {
        const identityFields = this.replyToEmailId
          ? (replyEnvelopeChanged(this.providerThreadedReply,
              root.dataset.toAddress || '', root.dataset.subject || '', this.toAddress, this.subject)
              ? [this.toAddress, this.subject] : [])
          : [this.toAddress, this.subject];
        const signatureEdited = this.includeSignature
          && this.signature.replace(/\s+/g, ' ').trim() !== this.defaultSignature.replace(/\s+/g, ' ').trim();
        return [...identityFields, this.ccManual, this.bccManual]
          .some((value) => String(value || '').trim().length > 0)
          || this.bodyHasUserContent()
          || signatureEdited
          || this.checkedValues('ccUserId').length > 0
          || this.checkedValues('bccUserId').length > 0
          || this.attachmentNames.length > 0;
      },

      bodyHasUserContent() {
        if (!this.$refs.editor) return false;
        let copy;
        if (this.mode === 'source') {
          const parsed = new DOMParser().parseFromString(sanitizePastedHtml(this.html), 'text/html');
          copy = parsed.body;
        } else {
          copy = this.$refs.editor.cloneNode(true);
        }
        copy.querySelectorAll('[data-signature="1"], [data-signature-spacer="1"]').forEach((node) => node.remove());
        return Boolean(copy.textContent.trim() || copy.querySelector('img'));
      },

      syncSignatureFromSource() {
        const parsed = new DOMParser().parseFromString(sanitizePastedHtml(this.html), 'text/html');
        const signature = parsed.body.querySelector('[data-signature="1"]');
        if (signature) {
          this.signature = signature.innerHTML;
          this.includeSignature = true;
        } else {
          this.includeSignature = false;
        }
      },

      restoreDraft() {
        let draft;
        try {
          const stored = localStorage.getItem(this.storageKey);
          if (!stored) return;
          draft = JSON.parse(stored);
        } catch (_) {
          return;
        }
        if (!draft || draft.version !== 1) return;

        if (!this.replyToEmailId || !this.providerThreadedReply) {
          this.toAddress = draft.toAddress || this.toAddress;
          this.subject = draft.subject || this.subject;
          if (!this.replyToEmailId) this.clientId = draft.clientId || '';
        }
        this.senderEmail = draft.senderEmail || this.senderEmail;
        this.ccManual = draft.ccManual || '';
        this.bccManual = draft.bccManual || '';
        this.html = draft.html || '';
        if (typeof draft.signatureHtml === 'string') {
          this.signature = draft.signatureHtml;
          this.signatureFromDraft = true;
        }
        this.mode = draft.mode === 'source' ? 'source' : 'rich';
        this.templateId = draft.templateId || '';
        this.includeSignature = typeof draft.includeSignature === 'boolean'
          ? draft.includeSignature
          : this.includeSignature;
        this.ccOpen = Boolean(draft.ccOpen || this.ccManual || (draft.ccUserIds || []).length);
        this.bccOpen = Boolean(draft.bccOpen || this.bccManual || (draft.bccUserIds || []).length);
        this.filesNeedReattach = (draft.attachmentNames || []).length > 0;
        this.restoreCheckedValues('ccUserId', draft.ccUserIds);
        this.restoreCheckedValues('bccUserId', draft.bccUserIds);
        this.draftRestored = true;
        this.draftStatus = 'Draft restored';
      },

      discardDraft() {
        if (!window.confirm('Discard this local draft? This cannot be undone.')) return;
        try {
          localStorage.removeItem(this.storageKey);
        } catch (_) {
          // Closing still works when storage is unavailable.
        }
        this.isSending = true;
        this.closeComposer();
      },

      closeComposer() {
        if (!this.isSending) this.saveDraft(true);
        if (this.inlineMode) {
          const container = root.closest('#reply-composer');
          const shouldClear = root.dispatchEvent(new CustomEvent('mail-composer-close', {
            bubbles: true,
            cancelable: true,
            detail: { draftKey: this.storageKey }
          }));
          if (shouldClear && container) {
            container.replaceChildren();
            const returnFocusTarget = this.returnFocusTarget;
            window.setTimeout(() => {
              if (returnFocusTarget?.isConnected) returnFocusTarget.focus();
            }, 0);
          }
          return;
        }
        this.$dispatch('close-modal');
      },

      handleEscape() {
        if (this.previewOpen) return;
        if (this.optionsOpen) this.optionsOpen = false;
        else if (!this.minimized && !this.inlineMode) this.minimized = true;
      },

      onSubmit(event) {
        if (this.attachmentError || this.senderLoading || this.senderError || this.isSending) {
          event.preventDefault();
          return;
        }
        if (this.mode === 'source') {
          this.html = sanitizePastedHtml(this.html);
          this.$refs.editor.innerHTML = this.html;
        }
        this.syncFromRich(false);
        this.saveDraft(true);
        this.isSending = true;
      }
    };
  };

  window.TalliMailComposerTest = {
    attachmentIssue,
    composeBody,
    draftKey,
    formatFileSize,
    previewRecipients,
    replyEnvelopeChanged,
    safeImageUrl,
    safeSignatureClasses,
    safeStyleValue,
    safeUrl
  };

  consumeSentDraftKeys(document);
  document.addEventListener('htmx:beforeRequest', (event) => {
    const trigger = event.detail?.elt || event.target;
    if (trigger?.getAttribute?.('hx-target') === '#reply-composer') inlineReturnFocus = trigger;
  });
  document.addEventListener('DOMContentLoaded', () => consumeSentDraftKeys(document));
  document.addEventListener('htmx:afterSwap', (event) => consumeSentDraftKeys(event.target));
})();
