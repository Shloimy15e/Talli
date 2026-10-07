# Talli Quick capture

The unpacked Manifest V3 extension tracks time, edits the running task description, creates projects, and records expenses against the existing Talli REST API. Recent projects follow the API's recent activity ordering. Expense entry includes date, currency, category, project, vendor, note, and billable status.

## Load or update

1. Open `chrome://extensions` and enable Developer mode.
2. For an existing installation, click **Reload** on Talli. For a new installation, choose **Load unpacked** and select this `chrome-extension` folder.
3. Open the popup's Settings. Enter the Talli server origin and an API token from Talli Settings.
4. Choose **Save connection**, approve access to that specific server, and return to Quick capture. Save verifies the connection before storing the credentials.

These steps require an explicit local installation or reload; the implementation tests do not install an extension or contact a real Talli server.

Servers use HTTPS; local development supports HTTP on `localhost` or `127.0.0.1`. The manifest declares optional host permissions; the Settings button requests the entered origin only. Existing users should reopen Settings and save once after updating to grant their server origin. Tokens are stored in local extension storage, never synced or placed in a URL. Unsaved token drafts from older versions are removed. Disconnect removes the saved token and its server permission; it leaves any server-side running timer running.

## Behavior

- Timer start and stop submissions share a lock. Other project start actions are disabled while a timer runs. Stop the timer before switching projects.
- Refresh checks the authoritative current timer and project list. Failed actions preserve input and show a recoverable message. Running context shows the project and its client, with explicit text when client details are unavailable.
- Requests time out after 12 seconds, including response-body loading. Mutations are never automatically replayed. After a timeout or network failure, verify the action in Talli before resubmitting; the server may have saved it.
- The badge polls once per minute and updates after timer or connection changes. `!` indicates that the background timer check failed.
- The local controls reuse the app's shared select implementation and design tokens. Client and expense-project menus support search; selectors support keyboard navigation, Enter, and Escape. Approved brand assets and Inter are bundled locally for extension CSP compatibility.

## Verification

Run `node --test chrome-extension/tests/api.test.cjs` from the repository root. The API tests exercise validation, non-JSON errors, cancellation, timeout uncertainty, and empty timer responses.

The browser suite requires an existing Playwright installation and Chrome. Set `PLAYWRIGHT_MODULE` to the existing Playwright package path if it is not available through normal module resolution, then run `node chrome-extension/tests/browser.test.cjs`. It runs a fresh headless Chrome browser with mocked Chrome APIs and intercepted REST requests. It checks project search, custom select keyboard behavior, start/stop duplicate prevention, failure/retry, expenses, project creation, settings permissions, stale responses, disconnect, and project/client context. Screenshots are written to `tests/artifacts`.

The mock suite verifies page behavior and request contracts. Actual extension loading, Chrome's permission prompt, service-worker suspension, and a real server connection require manual verification after installation. There is no time-history API in this workflow; the list represents projects rather than time entries.

`assets/select.js` and `assets/select.css` are local copies of the app's shared select controls. `assets/design-tokens.css` is a copy of the app tokens with its font URL adjusted for this folder. Keep these copies aligned when changing the app controls. Extension icons are scaled copies of the approved Talli icon; the old bar-chart icon generator has been retired.
