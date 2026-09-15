# Shloimy Elbaum email signature

[`signature.html`](signature.html) is the primary signature. It is a full redesign of the supplied signature around a compact logo-and-identity composition:

- one sign-off and one occurrence of the name;
- a clear 160px logo in a disciplined 170px brand column instead of the old 200px image block and oversized gap;
- Shloimy Elbaum as the visual anchor, with Founder & CEO directly beneath it;
- the logo carries the Dynamiq Solutions business name, avoiding a redundant company line;
- a quiet orange divider separates the logo and identity: vertical on desktop and horizontal when the layout stacks;
- email, both phone numbers, and the website organized on a consistent 19px contact rhythm;
- no Gmail or HubSpot wrappers, spacer tables, web fonts, or generic utility classes; the five namespaced classes only control the responsive signature layout.

[`signature-text.html`](signature-text.html) is the image-free alternative. It retains the same hierarchy and contact rhythm with a short orange hairline. Use it when avoiding remote images matters more than displaying the logo.

[`plain-text.txt`](plain-text.txt) is the matching text-only fallback. [`preview.html`](preview.html) renders the exact primary signature after a realistic message closing at desktop width and in a 320px mail pane, then shows the image-free alternative. Each option has its own Copy HTML action.

## Installation notes

- Paste only the table markup between `<!-- installable-signature:start -->` and `<!-- installable-signature:end -->` into the sender profile’s HTML signature field. The surrounding style block makes the standalone file responsive for direct review; outgoing email wrappers provide the same scoped rule for stored fragments.
- Keep the visible phone numbers as supplied. Their links use normalized international targets: `tel:+19299934115` and `tel:+972553319614` for the Israeli `055 331 9614` number.
- Both fragments use presentation tables, inline styles, and Arial with Helvetica and sans-serif fallbacks. They have been browser-verified in the local preview; individual mail clients have not been tested.
- The primary uses two inline-block regions inside one presentation-table cell. They fit side by side in the 460px layout, then a scoped mobile rule trims the logo image's vertical whitespace inside a 44px viewport, stacks the content-width contact region below it, insets the contact region 10px to align with the visible logo, hides the spacer, and changes the divider from left to top. Email addresses and phone-number units stay intact instead of being squeezed into a leftover column. Clients without media-query support retain the readable inline vertical-divider fallback.
- The primary signature uses the original signature’s canonical `data-os` logo URL: `https://lh3.googleusercontent.com/d/1lfKgLhKID6PZSIddPdfC6R_p7XwnE8Qu`. Recipients need network access and permission from that host for the image to appear, and some mail clients block remote images until allowed.
- The primary HTML signature was installed locally on September 15, 2026 for `Shloimy Elbaum | Dynamiq Solutions` (`shloimy@dynamiq.dev`). The image-free alternative remains available as a separate option.
- The Info, Billing, Finance, Sales, and Support profiles use [`role.html`](role.html). Its logo identifies the company; a smaller role label describes the mailbox without imitating a person's name or implying a team. Substitute escaped `{{role}}` and `{{email}}` values. The Info profile uses “General enquiries”; the others use “Billing”, “Finance”, “Sales”, and “Support”. Role signatures omit Shloimy’s personal phone numbers and title.
