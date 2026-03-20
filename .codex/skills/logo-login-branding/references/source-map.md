# Source Map

Use these files when creating previews and applying the selected branding.

## Angular UI

- `ui/src/app/auth/login/login.component.ts`
- `ui/src/app/auth/login/login.component.html`
- `ui/src/app/auth/login/login.component.css`
- `ui/src/app/shell/app-shell.component.ts`
- `ui/src/app/shell/app-shell.component.html`
- `ui/src/app/shell/app-shell.component.css`
- `ui/src/app/shared/company-context.service.ts`
- `ui/src/app/company-settings/company-settings-page.component.ts`
- `ui/src/app/company-settings/company-settings-page.component.html`

## Middleware

- `mw/src/main/java/com/aas/mw/service/MasterDataService.java`
- `mw/src/main/java/com/aas/mw/controller/MasterDataController.java`
- `mw/src/main/java/com/aas/mw/client/ErpNextClient.java`

## Screenshot / Preview Helpers

- `ui/scripts/take-chrome-screenshots.mjs`
- `ui/playwright.config.ts`
- `ui/e2e/ui-audit-screens.spec.ts`

## Preview Output Folder

- `ui/test-artifacts/login-branding/<company-slug>/`

## Helpful Searches

- `rg "logo|brand|company-context|login" ui/src/app mw/src/main/java`
- `rg "company_logo|logo_url|default_letter_head|letter_head_image" mw ui`
