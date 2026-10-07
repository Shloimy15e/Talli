---
name: Talli
description: A calm operational design foundation for client work, billing, and communications.
colors:
  ink: "#0f2a44"
  muted: "#6b7280"
  rail: "#0f2a44"
  surface: "#ffffff"
  subtle: "#f1f5f9"
  canvas: "#fafafa"
  line: "#e5e7eb"
  border-strong: "#94a3b8"
  accent: "#f97316"
  accent-hover: "#0f2a44"
  accent-foreground: "#ffffff"
  accent-ink: "#a84308"
  focus: "#a84308"
  selected: "#fff1e7"
  hover: "#f1f5f9"
  active: "#e5e7eb"
  rail-ink: "#cbd5e1"
  rail-hover: "#193a57"
  rail-active: "#234663"
  rail-accent: "#fb923c"
  selection: "#ffedd5"
  selection-ink: "#0f2a44"
  success-surface: "#edf8f1"
  success: "#386545"
  success-line: "#dceee2"
  warning-surface: "#fff4ed"
  warning: "#94431a"
  warning-line: "#f4d5bc"
  danger-surface: "#fff3f0"
  danger: "#9e4033"
  danger-line: "#f4ddd6"
  disabled: "#7c8796"
  disabled-surface: "#d6dae0"
typography:
  headline:
    fontFamily: "Inter, system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "-0.65px"
  title:
    fontFamily: "Inter, system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "16px"
    fontWeight: 650
    lineHeight: 1.25
    letterSpacing: "-0.35px"
  body:
    fontFamily: "Inter, system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.85
    letterSpacing: "normal"
  label:
    fontFamily: "Inter, system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "normal"
  metadata:
    fontFamily: "Inter, system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "10px"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "normal"
rounded:
  compact: "6px"
  control: "8px"
  panel: "10px"
  pill: "999px"
  circular: "50%"
spacing:
  hairline: "1px"
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  xxl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.accent-foreground}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "11px 13px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
    textColor: "{colors.accent-foreground}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "9px 13px"
  icon-button:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    rounded: "{rounded.compact}"
    size: "32px"
  search-field:
    backgroundColor: "{colors.subtle}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "9px 11px"
  conversation-selected:
    backgroundColor: "{colors.selected}"
    textColor: "{colors.ink}"
    rounded: "0"
    padding: "17px 19px 17px 23px"
---

# Design System: Talli

## Overview

**Creative North Star: "The Quiet Workbench"**

Talli should feel like a finely made work tool: calm enough for sustained use, compact enough for real operations, and familiar on first contact. Apple’s interface discipline is a useful reference—clear hierarchy, restrained chrome, native-feeling controls, and content that receives more visual weight than the interface—translated into Talli’s Inter typography and warm orange identity.

The shared foundation is implemented in `src/main/resources/static/css/design-tokens.css`; Mail is its first complete reference surface. The operational screens, sign-in, invitations, and client portal share this foundation through semantic Tailwind utilities in `js/ui/theme.js`. Reusable controls belong in generic `ui-*` assets and must stay free of mail-domain assumptions. Inbox rows, the conversation reader, and the composer remain Mail patterns. Evidence also comes from `PRODUCT.md`, `.impeccable/surfaces/mail.md`, the mail layout and email templates, and the implemented Mail CSS and JavaScript.

**Key Characteristics:**

- Quiet operational surfaces that let client, financial, and communication content lead.
- Cool white and mist-gray surfaces separated by hairlines rather than decorative cards.
- Orange reserved for primary action, current selection, focus, and meaningful links.
- Inter throughout, with compact metadata and comfortable reading rhythm.
- Progressive disclosure for secondary choices and technical detail.
- Familiar keyboard behavior, visible focus, honest status feedback, and 44px mobile action targets.

## Colors

The palette is a quiet cool-neutral field with one warm action family. These semantic roles are defined as `--ui-*` custom properties in `design-tokens.css`. Deep navy anchors global navigation; it does not spread into ordinary content panels or create a competing theme.

### Primary

- **Dynamiq Orange** (`accent`): The canonical `#F97316` brand fill for primary actions, selection marks, checks, and form accents.
- **Dynamiq Navy Hover** (`accent-hover`): Navy for primary-action hover, matching the approved Developers button treatment.
- **Accent Foreground** (`accent-foreground`): White text and icons on orange fills, matching the chosen Dynamiq brand treatment for Compose and Send.
- **Accent Ink** (`accent-ink`): Dark orange for links and small foreground marks on light surfaces.
- **Warm Selection** (`selected`): Selected list or control state; its calm filled treatment replaces ornamental indicators.

### Neutral

- **Working Ink** (`ink`): Primary text and high-value labels.
- **Slate Metadata** (`muted`): Dates, counts, previews, recipient summaries, and secondary controls.
- **Hover and Active Mist** (`hover`, `active`): Related neutral state fills; active is slightly firmer than hover.
- **Workspace Navy Family** (`rail`, `rail-ink`, `rail-hover`, `rail-active`, `rail-accent`): Global workspace navigation only.
- **Paper** (`surface`): Reading, list, and composing surfaces.
- **Cool Mist** (`subtle`): Search fields, metadata disclosures, toolbars, and low-emphasis grouped controls.
- **Canvas Mist** (`canvas`): Folder navigation and composer chrome.
- **Hairline** (`line`): Pane boundaries, row separators, and field dividers.
- **Strong Hairline** (`border-strong`): The perimeter of a detached composer, where a floating edge needs more definition.
- **Text Selection Pair** (`selection`, `selection-ink`): Browser text selection that remains legible and recognizably Talli.

### Tertiary

- **Success Family** (`success-surface`, `success`, `success-line`): Confirmed completion feedback.
- **Warning Family** (`warning-surface`, `warning`, `warning-line`): Recoverable attention states.
- **Danger Family** (`danger-surface`, `danger`, `danger-line`): Errors and failed actions.
- **Disabled Pair** (`disabled-surface`, `disabled`): Unavailable controls that stay legible without appearing actionable.

**The One Warm Voice Rule.** Orange is Talli’s only brand accent. Use it for action and state, never as broad decoration.

**The Quiet Chrome Rule.** Dark navy belongs to global workspace navigation. Local toolbars, dialogs, editors, and reading chrome use cool neutral surfaces so Talli reads as one system.

## Typography

**Display Font:** None on this operating surface.

**Body Font:** Inter Variable or Inter, with platform system and `sans-serif` fallbacks.

**Character:** Inter keeps dense operational information neutral and crisp. Hierarchy comes from size, weight, leading, and subtle negative tracking rather than mixing typefaces. Arial is used only inside portable email-signature HTML where client compatibility requires it.

### Hierarchy

- **Headline** (`headline`): The strongest task-specific content, such as a conversation subject. Keep wrapping natural and allow long values to break safely.
- **Title** (`title`): Page, section, and pane headings.
- **Body** (`body`): Sustained reading content, capped near 75 characters per line when the format allows it.
- **Label** (`label`): Navigation, buttons, field text, names, and concise values.
- **Metadata** (`metadata`): Dates, counts, hints, sizes, and secondary details. Use tabular numerals for counts and dates.

Weights cluster at 400, 500, 600, and 650. Unread conversations may use 700 for contact and subject only. Sentence case is the default; uppercase display labels do not belong in this workspace.

**The Content Leads Rule.** The object being worked on is the strongest type in the view. Supporting identity and content follow; system machinery and provider references stay quiet until requested.

## Layout

The shared spacing rhythm uses 4, 8, 12, 16, 24, and 32px steps. Layout follows the work: persistent selection and detail may justify adjacent panes, while a linear task should remain one clear column. At narrow widths, preserve useful target sizes and show one task at a time rather than compressing desktop structure.

### Reference implementation: Mail

Mail occupies the viewport (`100dvh`) and keeps the surrounding application shell out of the reading flow. At standard desktop widths, its implemented grid is a 60px workspace rail, 174px mailbox column, 328px conversation list, and a flexible reader. At 1500px and above, the mailbox and list grow to 190px and 380px, while reader gutters expand fluidly. Each working pane owns its scroll position so moving through mail does not lose reading or list context.

At 1150px, the mailbox and list compress to 148px and 286px. At 900px, folders become a 200px overlay and the persistent layout becomes list plus reader. At 650px, the rail disappears and Mail shows one task pane at a time: list first, then reader, with a visible back action. The composer becomes full-screen on mobile and may minimize to a bottom strip.

Mail conversation rows are intentionally dense but not cramped: a 107px minimum height carries contact, date, subject, thread count, two preview lines, and exceptional status without resembling an audit table. Reader gutters begin near 34px on desktop and tighten to 20px on mobile.

**The Context Stays Put Rule.** Selection changes update the reader while the list remains visible and restores its previous scroll position.

**The One Task Pane Rule.** Below 650px, never squeeze the list and reader side by side. Show the current task at full width with a clear path back.

## Elevation & Depth

Talli is flat by default. Hierarchy comes from adjacent tones and 1px hairlines. Use shadow only when a surface actually floats. Mail demonstrates the rule: its desktop composer uses a two-layer ambient shadow, the temporary folder drawer uses a directional shadow, and the network error floats above the workspace. Inline reply composers and ordinary panels stay shadowless.

### Shadow Vocabulary

- **Composer Float** (`0 16px 48px #1720331c, 0 3px 12px #17203312`): Detached desktop composer only.
- **Folder Drawer** (`7px 0 22px #25334a18`): Temporary folder navigation below 900px.
- **Network Alert** (`0 5px 20px #16263826`): Transient retry feedback above the workspace.
- **Barely Raised** (`0 2px 4px #1c304205`): Reply prompt and similarly interactive, near-flat surfaces.

**The Earned Elevation Rule.** A shadow communicates floating or temporary position. It is not a default card treatment.

## Shapes

Controls use a compact 6px radius, standard controls use 8px, and panels or floating windows use 10px. Chips are pills and avatars are circular. Rows, sections, and primary panes remain rectangular so operational surfaces read as one continuous instrument rather than a stack of cards.

Borders are cool 1px hairlines. Selection uses a calm warm tinted surface without an extra leading indicator. Focus uses a 2px dark-orange ring with 3px offset; it must remain visible on white, soft gray, and selected backgrounds.

**The Continuous Surface Rule.** Do not round every region. Reserve visible corner treatment for controls, disclosures, temporary windows, and compact contained objects.

## Components

### Buttons

- **Primary:** A restrained solid Dynamiq-orange control with white text and icons. Compose uses 8px corners and 11px by 13px padding; the denser Send control uses 6px corners and a 36px height.
- **Secondary:** White with a hairline border, working ink, 8px corners, and 9px by 13px padding.
- **Icon:** A 32px square with 6px corners. It is transparent at rest and gains a cool-gray hover fill; every icon-only control has an accessible name and title where discovery helps.
- **Hover / Focus:** Hover changes color without dramatic lift. Focus uses the shared 2px warm outline. Disabled actions remain legible, visibly muted, and non-interactive.
- **Mobile targets:** Composer header and footer actions grow to at least 44px; desktop-only minimize and expand controls disappear.

### Chips

- **Attachments:** White pill with a hairline border, compact label text, filename truncation, and an individually named remove action.
- **Counts:** Small neutral badges with tabular numerals; they inform hierarchy without competing with subjects.

### Cards / Containers

- **Conversation row:** One row per conversation. Contact and date form the first line; subject and count form the second; preview occupies at most two lines. Selected and unread are independent states.
- **Message:** A hairline-separated disclosure. The newest or selected message opens by default; closed messages show a preview, while open messages replace it with recipient context.
- **Delivery warning:** A soft warm panel inside the affected message. State exactly what failed and provide recorded detail without implying a retry or delivery outcome the system cannot prove.

### Inputs / Fields

- **Search:** Soft neutral fill, hairline border, compact search icon, and a warm focus treatment. `/` focuses it when the user is outside an editor or field.
- **Composer fields:** Borderless inputs arranged on divided rows. Labels remain aligned in a narrow first column; Cc, Bcc, client, and template controls disclose only when needed.
- **Checkboxes:** Real form inputs use a restrained 16px square, 4px corners, and a crisp neutral border. Checked and indeterminate states use the canonical orange fill with a white check or dash. Keyboard focus uses the shared outline; disabled states remain visibly muted. Mobile labels provide a comfortable target, and forced-color mode uses native checkbox rendering.
- **Error / Disabled:** Errors use the danger pair and `role="alert"`; asynchronous status uses quiet neutral feedback. Sending stays disabled while sender data, attachments, or submission state are unresolved.

### Selects

Reusable selects belong to the generic UI layer (`src/main/resources/static/js/ui/select.js`, `src/main/resources/static/css/ui/select.css`) and use `ui-select`, `data-ui-select`, and `window.TalliSelect`. The native select remains the form-value and label source while the enhanced control renders primary and secondary labels, disabled and selected states, and a viewport-aware popup.

Search is opt-in through `data-ui-select-search`. It matches primary, secondary, and full labels case-insensitively and shows “No matches” honestly. Controls without visible search retain typeahead. Arrow keys, Home, End, Enter, Space, Escape, Tab, outside click, resize, scrolling, dynamic option refresh, and HTMX cleanup have defined behavior. Triggers, options, and search fields grow to at least 44px on mobile. Domain data and filtering policy remain with the calling surface; the generic component does not know about mail, clients, templates, or addresses.

### Navigation

- **Workspace rail:** A 60px deep-navy anchor with 40px icon targets. Current location uses a quiet navy fill and warm icon; hover uses a lighter navy fill.
- **Mailboxes:** A 174px cool-neutral list on desktop. Active folders use neutral emphasis and weight, while counts align to the trailing edge.
- **Conversation list:** A persistent neighboring pane at desktop sizes. Arrow keys move selection, and pagination, folder, and search state remain visible and stable.

The rail, mailboxes, and conversation list above are Mail’s current reference implementation. Only the rail’s visual tokens and generic interaction rules belong to the shared foundation; mailbox and conversation behavior remain Mail-specific.

### Composer

The desktop composer is a nonblocking window anchored to the lower-right edge. It can minimize, expand, save-and-close, and stay present while the user references the conversation list or reader. Inline replies reuse the same fields, editor, toolbar, attachment handling, sender logic, and draft semantics, but sit in the reading flow with neutral header chrome and no floating shadow.

Keep the footer’s first-level choices to Send, Attach, Formatting, Message options, Preview, and Discard. Client, template, HTML source, and signature inclusion belong in the progressively disclosed Message options panel. The selected sender’s signature is visible and editable in the message body. Changing senders replaces that signature while preserving the message; drafts and previews retain one copy. Mobile hides window-management controls and turns the composer into a focused full-screen task.

Drafts are local to the signed-in actor and either the new-message slot or reply conversation. After 500ms of inactivity, status progresses through “Saving…” to “Saved locally.” Restoration says “Draft restored”; unavailable browser storage says “Local draft unavailable.” Attachment names may be remembered, but files must be reattached, and the interface says so. A confirmed successful send removes the corresponding local draft marker.

Rich composition sanitizes pasted, source, and sender-signature HTML to an explicit allowlist that preserves safe email tables and inline styling. Links accept HTTP, HTTPS, mailto, and tel; images accept HTTP, HTTPS, and supported raster data URLs. Message previews and the shared HTML preview dialog use sandboxed iframes. Sender settings expose a Preview signature action instead of squeezing signatures into list rows; the same dialog previews unsaved create/edit content. Read-message HTML also renders in a sandboxed iframe with scripts disabled, safe external-link handling, no referrer, responsive images, and a plain-text fallback.

### Feedback and Empty States

Success and error notices stay adjacent to the affected list or composer. A network failure produces a persistent retry alert rather than silently failing. Empty mailboxes distinguish an empty folder from no search results and offer the next real action. Older conversations without a valid reply target say that threaded reply is unavailable and offer a new message; they never show a dead Reply control.

Keyboard shortcuts are `C` for compose, `R` for reply, `/` for search, and Up/Down for list movement. They do not fire while a field, editor, modal, or inline composer owns focus. Escape closes a preview or options panel first, then minimizes a detached composer. All interactions preserve a visible focus indicator and respect reduced-motion preferences.

## Do's and Don'ts

### Do:

- **Do** keep the object being worked on visually ahead of metadata and system chrome.
- **Do** preserve side-by-side selection and detail context on capable viewports when the task benefits from it, including independent scroll state where needed.
- **Do** build reusable controls on the shared `--ui-*` tokens and generic `ui-*` assets.
- **Do** keep reusable component APIs free of client, template, email, and other domain assumptions.
- **Do** disclose Cc, Bcc, templates, signature preview, source editing, and message details only when requested.
- **Do** state draft scope and unavailable actions honestly, especially local-only drafts, attachment reattachment, and legacy reply limits.
- **Do** pair color with text, weight, shape, or iconography for selection, failure, and unread state.

### Don't:

- **Don't** spread the Mail pane topology to screens that do not need persistent selection and detail context.
- **Don't** mix dark application chrome into local tools or ordinary content panels; reserve navy for global workspace navigation.
- **Don't** turn conversations into audit-table rows, card grids, or isolated rounded tiles.
- **Don't** expose provider references, delivery diagnostics, or full recipient metadata before the user asks for details.
- **Don't** imply cloud draft sync, successful delivery, reply threading, or retained attachment files unless the implementation proves it.
- **Don't** add decorative accent colors, gratuitous motion, or oversized marketing typography to this operating surface.

## Dynamiq brand integration

The approved Dynamiq Developers brand supplies navy #0F2A44, orange #F97316, off-white #FAFAFA, the local Inter variable font, and unchanged SVG marks. Talli keeps its product name and Mail's compact operational layout. Primary buttons use white text and icons on orange, changing to navy with white foreground on hover, as implemented in the Developers site's shared button style. Inline links use the darker accent-ink role. Checkboxes retain native form and keyboard behavior with orange selection and a white check. The brand mark appears in navigation and authentication, with the horizontal wordmark as attribution. Shared tables use quiet headers and contained horizontal scrolling; summary grids adapt to smaller screens.
