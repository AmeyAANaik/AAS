# Implementation Notes

## Recommended Execution Pattern

1. Receive a company logo file path from the user.
2. Create a working slug from the company name.
3. Produce 2 to 4 branded UI variants.
4. Render screenshots for each variant and save them under:
   - `ui/test-artifacts/login-branding/<company-slug>/`
5. Ask the user to choose one option.
6. Apply the chosen option cleanly and remove unused temporary variant code if necessary.
7. Capture a final screenshot after implementation.

## Branding Scope

Prefer this order:
- Angular login page visuals
- Angular shell/header branding
- middleware/company profile wiring
- ERPNext company logo fields

Treat deeper ERPNext login theming as conditional work that depends on actual override points existing in the repo or ERP site.

## ERPNext Reality Check

This repo exposes company branding data through middleware, but it does not obviously include a custom Frappe app for deep ERPNext login-page visual overrides.

Therefore:
- Apply company logo and related company identity fields where supported.
- Verify whether ERPNext site branding can be changed through standard settings alone.
- If full ERPNext login theming is not supported from this repo, state that constraint explicitly and stop short of inventing unsupported changes.

## Preview Quality Bar

- Make options visually distinct, not tiny color shifts.
- Use the logo as the source of accent color, shape language, and tone.
- Keep the selected option accessible and responsive.
- Avoid hardcoding one brand name like `SCM Console` if the goal is multi-company branding.
