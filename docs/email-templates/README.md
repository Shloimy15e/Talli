# Email template previews

The four composer fixtures use illustrative correspondence that does not represent a client or project. The invoice and reminder fixtures use synthetic billing records. Every fixture appends the current documented Billing role signature from `../email-signatures/role.html` so template and signature spacing are reviewed together.

`EmailTemplateCatalogTest` renders every preview through `EmailTemplateCatalog.wrap(...)` and verifies the checked-in HTML byte for byte. To refresh the artifacts after an intentional catalog change, run:

```powershell
.\mvnw.cmd -DemailTemplatePreviews.update=true -Dtest=EmailTemplateCatalogTest test
```

`TransactionalEmailTemplateRenderingTest` renders the invoice and reminder fixtures through Thymeleaf and verifies the checked-in HTML byte for byte. To refresh them after an intentional template change, run:

```powershell
.\mvnw.cmd -DtransactionalEmailPreviews.update=true -Dtest=TransactionalEmailTemplateRenderingTest test
```

Open the individual HTML files at desktop and 320px widths to review the final email output:

- `branded.html` — everyday correspondence with a quiet letterhead
- `branded-notice.html` — body-first correspondence in a rounded panel
- `formal.html` — spacious formal letter composition
- `minimal.html` — content-first message
- `invoice.html` — invoice summary with an optional ACH payment action
- `reminder.html` — responsive multi-invoice payment reminder

Open `preview.html` to compare all six layouts together, with a 320px width toggle. The two generators embed their exact fixtures in the gallery so it works from either a local file or the application preview route.
