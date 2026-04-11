---
name: logo-login-branding
description: Turn a provided company logo into branded login-page concepts for AAS, save preview images in one folder for user selection, then apply the chosen design to the Angular front page and ERPNext/company branding fields. Use when the user wants company-specific branding, multiple design samples from a logo, login-page redesign, or coordinated logo/theme rollout across the app and ERPNext.
---

# Logo Login Branding

Create branded preview concepts from a company logo before changing production-facing files. The core contract is: generate multiple samples, store them together as image previews, let the user choose, then apply the selected design cleanly.

## Workflow

1. Read the provided logo path or attachment.
2. Inspect the current implementation files using `references/source-map.md`.
3. Create 2 to 4 distinct login/front-page concepts from the logo before applying any final branding.
4. Save preview screenshots in one folder:
   - `ui/test-artifacts/login-branding/<company-slug>/`
   - Use clear names such as `option-a.png`, `option-b.png`, `option-c.png`.
5. Present the preview image paths to the user and wait for a selection.
6. Apply only the selected option to the Angular login/front page and shared company branding surfaces.
7. Update ERPNext-facing company branding fields where the repo supports them.
8. Capture final screenshots after the chosen design is applied.

## What To Brand

At minimum, review and update:
- Angular login page
- shared app header/shell branding
- company logo usage and company-context loading
- company settings flow if logo fields need to be updated through middleware

If the user explicitly wants ERPNext branding too:
- Prefer standard company/logo fields first.
- If full ERPNext login-page theming is requested, inspect whether the repo contains a custom Frappe app or override point.
- If no ERPNext theming hook exists in this repo, say so clearly and apply the maximum supported branding instead of pretending full theming exists.

## Preview Rules

- Do not overwrite the final design before the user picks one.
- Make the options meaningfully different:
  - one conservative/corporate
  - one bold/hero-driven
  - one minimal/product-focused
- Reuse colors from the logo or close neutrals derived from it.
- Keep screenshots in a single company-specific folder so the user can compare quickly.
- Prefer desktop-first previews, then ensure the selected option still works on mobile.

## Implementation Rules

- Preserve the existing app architecture and data flow.
- Prefer wiring branding through existing company-context fields instead of hardcoding one company name or logo.
- Replace static login branding with configurable company-aware branding when practical.
- If an uploaded logo should become the main company logo, wire it through supported ERP/company fields such as `company_logo` or `logo` where available.
- Keep fallback initials or text branding for cases where no logo exists.

## Deliverables

For each use of this skill, produce:
- preview images in `ui/test-artifacts/login-branding/<company-slug>/`
- a short summary of the options
- the selected implementation applied in code
- final screenshot paths
- a note describing what was applied in Angular and what was applied in ERPNext

## AAS-Specific Notes

- Current login branding is hardcoded in `ui/src/app/auth/login/login.component.html` and `login.component.css`.
- The app shell already reads company logo data from company context.
- Middleware exposes company profile fields including `logo_url` and updates `company_logo` / `logo`.
- The repo currently contains ERPNext docker setup, but no obvious custom Frappe app for deep ERPNext login-page theming. Verify before claiming full ERPNext visual overrides.

## References

- Use `references/source-map.md` for the main files and commands.
- Use `references/implementation-notes.md` for rollout expectations and limits.
