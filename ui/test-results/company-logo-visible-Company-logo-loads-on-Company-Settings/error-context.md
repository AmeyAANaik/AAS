# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: company-logo-visible.spec.ts >> Company logo loads on Company Settings
- Location: e2e/company-logo-visible.spec.ts:3:5

# Error details

```
Error: expect(received).toBeGreaterThan(expected)

Expected: > 0
Received:   0
```

# Page snapshot

```yaml
- generic [ref=e4]:
  - banner [ref=e5]:
    - generic [ref=e7]:
      - img "Shree Siddhivinayak Suppliers logo" [ref=e8]
      - generic [ref=e9]:
        - generic [ref=e10]: Shree Siddhivinayak Suppliers
        - generic [ref=e11]: INR
    - generic [ref=e12]:
      - 'link "Reviews pending: 262" [ref=e13] [cursor=pointer]':
        - /url: /bill-review
        - generic [ref=e14]: notifications
        - generic [ref=e15]: Reviews
        - generic [ref=e16]: "262"
      - button "Dark mode" [ref=e17] [cursor=pointer]:
        - generic [ref=e18]: dark_mode
        - generic [ref=e19]: Dark mode
      - button "Administrator Admin" [ref=e21] [cursor=pointer]:
        - generic [ref=e22]: A
        - generic [ref=e23]:
          - strong [ref=e24]: Administrator
          - generic [ref=e25]: Admin
        - generic [ref=e26]: expand_more
  - generic [ref=e27]:
    - complementary [ref=e28]:
      - generic [ref=e30]:
        - generic [ref=e31]: Workspace
        - generic [ref=e32]: Home / Company Settings
        - generic [ref=e33]: Shree Siddhivinayak Suppliers · Admin
      - navigation [ref=e34]:
        - generic [ref=e35]: Home
        - link "Dashboard" [ref=e36] [cursor=pointer]:
          - /url: /admin/dashboard
          - generic [ref=e37]: dashboard
          - text: Dashboard
      - navigation [ref=e38]:
        - generic [ref=e39]: Procure
        - link "Orders" [ref=e40] [cursor=pointer]:
          - /url: /orders
          - generic [ref=e41]: receipt_long
          - text: Orders
      - navigation [ref=e42]:
        - generic [ref=e43]: Inventory
        - link "Stock" [ref=e44] [cursor=pointer]:
          - /url: /stock
          - generic [ref=e45]: inventory_2
          - text: Stock
      - navigation [ref=e46]:
        - generic [ref=e47]: Finance
        - link "Bills / Invoices" [ref=e48] [cursor=pointer]:
          - /url: /bills
          - generic [ref=e49]: account_balance_wallet
          - text: Bills / Invoices
      - navigation [ref=e50]:
        - generic [ref=e51]: Master Data
        - link "Vendors" [ref=e52] [cursor=pointer]:
          - /url: /vendors
          - generic [ref=e53]: local_shipping
          - text: Vendors
        - link "Branches" [ref=e54] [cursor=pointer]:
          - /url: /branches
          - generic [ref=e55]: hub
          - text: Branches
        - link "Categories" [ref=e56] [cursor=pointer]:
          - /url: /categories
          - generic [ref=e57]: category
          - text: Categories
        - link "Items" [ref=e58] [cursor=pointer]:
          - /url: /items
          - generic [ref=e59]: inventory
          - text: Items
      - navigation [ref=e60]:
        - generic [ref=e61]: Reports
        - link "Reports" [ref=e62] [cursor=pointer]:
          - /url: /reports
          - generic [ref=e63]: query_stats
          - text: Reports
      - navigation [ref=e64]:
        - generic [ref=e65]: Operations
        - link "Vendor Operations" [ref=e66] [cursor=pointer]:
          - /url: /vendor-ops
          - generic [ref=e67]: local_shipping
          - text: Vendor Operations
        - link "Branch Operations" [ref=e68] [cursor=pointer]:
          - /url: /branch-ops
          - generic [ref=e69]: storefront
          - text: Branch Operations
    - main [ref=e70]:
      - generic [ref=e71]:
        - generic [ref=e72]:
          - generic [ref=e73]: Home / Company Settings
          - generic [ref=e74]: Operational workspace
        - generic [ref=e75]: INR
      - generic [ref=e78]:
        - generic "Company Settings" [ref=e79]:
          - generic [ref=e81]:
            - generic [ref=e82]: Settings
            - generic [ref=e83]: Company Settings
            - generic [ref=e84]: Update the application company identity shown throughout the shell, documents, and order workflow.
        - generic [ref=e85]:
          - generic [ref=e86]:
            - generic [ref=e87]:
              - img "Company logo" [ref=e88]
              - generic [ref=e89]:
                - generic [ref=e90]: Company profile
                - generic [ref=e91]: Shree Siddhivinayak Suppliers
                - generic [ref=e92]: Application identity and document defaults
            - generic [ref=e93]:
              - generic [ref=e94]:
                - generic [ref=e95]: Abbr
                - strong [ref=e96]: AAS
              - generic [ref=e97]:
                - generic [ref=e98]: Currency
                - strong [ref=e99]: INR
              - generic [ref=e100]:
                - generic [ref=e101]: Country
                - strong [ref=e102]: India
          - generic [ref=e103]:
            - generic [ref=e104]:
              - generic [ref=e105]:
                - generic [ref=e106]: Company logo
                - generic [ref=e107]: Upload the logo and optional signature used across the shell and GST invoice PDFs.
              - generic [ref=e108]:
                - generic [ref=e109]:
                  - img "Company logo preview" [ref=e110]
                  - generic [ref=e111]:
                    - generic [ref=e112]: Current company logo
                    - generic [ref=e113]: company_logob1a533.jpeg
                - generic [ref=e114]:
                  - button "Replace Logo" [ref=e115] [cursor=pointer]:
                    - generic [ref=e116]: Replace Logo
                  - generic [ref=e119]: "Recommended: square or landscape PNG/JPG with a clean background."
                - generic [ref=e120]:
                  - generic [ref=e121]: Optional
                  - generic [ref=e122]:
                    - generic [ref=e123]: Authorized signature
                    - generic [ref=e124]: Optional. Upload a signature image to show it in the invoice footer signatory section.
                - generic [ref=e125]:
                  - button "Upload Signature" [ref=e126] [cursor=pointer]:
                    - generic [ref=e127]: Upload Signature
                  - generic [ref=e130]: Optional. Best with transparent PNG and a wide signature crop.
            - generic [ref=e131]:
              - generic [ref=e132]:
                - generic [ref=e133]: Primary details
                - generic [ref=e134]: Core identity values used across the shell and application records.
              - generic [ref=e135]:
                - generic [ref=e136]:
                  - generic [ref=e137]:
                    - generic [ref=e138]: Company Name
                    - generic [ref=e139]: Displayed across the shell, documents, and workflows.
                  - generic [ref=e142]:
                    - generic [ref=e143]:
                      - text: Company Name
                      - generic [ref=e144]: "*"
                    - textbox "Company Name" [ref=e146]: Shree Siddhivinayak Suppliers
                - generic [ref=e148]:
                  - generic [ref=e149]:
                    - generic [ref=e150]: Abbreviation
                    - generic [ref=e151]: Short identifier used across records.
                  - generic [ref=e154]:
                    - generic [ref=e155]:
                      - text: Abbreviation
                      - generic [ref=e156]: "*"
                    - textbox "Abbreviation" [ref=e158]: AAS
                - generic [ref=e160]:
                  - generic [ref=e161]:
                    - generic [ref=e162]: Default Currency
                    - generic [ref=e163]: Primary currency used on generated documents.
                  - generic [ref=e166]:
                    - generic [ref=e167]:
                      - text: Default Currency
                      - generic [ref=e168]: "*"
                    - textbox "Default Currency" [ref=e170]: INR
                - generic [ref=e172]:
                  - generic [ref=e173]:
                    - generic [ref=e174]: Country
                    - generic [ref=e175]: Registered operating country.
                  - generic [ref=e178]:
                    - generic [ref=e179]: Country
                    - textbox "Country" [ref=e181]: India
            - generic [ref=e183]:
              - generic [ref=e184]:
                - generic [ref=e185]: Document defaults
                - generic [ref=e186]: Billing and compliance fields used when documents are generated downstream.
              - generic [ref=e187]:
                - generic [ref=e188]:
                  - generic [ref=e189]:
                    - generic [ref=e190]: Default Letter Head
                    - generic [ref=e191]: Header used on invoices and related paperwork.
                  - generic [ref=e194]:
                    - generic [ref=e195]: Default Letter Head
                    - textbox "Default Letter Head" [ref=e197]
                - generic [ref=e199]:
                  - generic [ref=e200]:
                    - generic [ref=e201]: Tax ID
                    - generic [ref=e202]: Compliance identifier printed on documents.
                  - generic [ref=e205]:
                    - generic [ref=e206]: Tax ID
                    - textbox "Tax ID" [ref=e208]
                - generic [ref=e210]:
                  - generic [ref=e211]:
                    - generic [ref=e212]: Bank Beneficiary Name
                    - generic [ref=e213]: Account holder name shown on the GST invoice bank-details block.
                  - generic [ref=e216]:
                    - generic [ref=e217]: Bank Beneficiary Name
                    - textbox "Bank Beneficiary Name" [ref=e219]: Shree Siddhivinayak Suppliers
                - generic [ref=e221]:
                  - generic [ref=e222]:
                    - generic [ref=e223]: Bank Name
                    - generic [ref=e224]: Bank name printed on the right side of the GST invoice.
                  - generic [ref=e227]:
                    - generic [ref=e228]: Bank Name
                    - textbox "Bank Name" [ref=e230]: RBL Bank
                - generic [ref=e232]:
                  - generic [ref=e233]:
                    - generic [ref=e234]: Bank Account Number
                    - generic [ref=e235]: Beneficiary account number used for invoice payments.
                  - generic [ref=e238]:
                    - generic [ref=e239]: Bank Account Number
                    - textbox "Bank Account Number" [ref=e241]: "407264981108"
                - generic [ref=e243]:
                  - generic [ref=e244]:
                    - generic [ref=e245]: Bank IFSC Code
                    - generic [ref=e246]: IFSC code shown for NEFT/RTGS/IMPS transfers.
                  - generic [ref=e249]:
                    - generic [ref=e250]: Bank IFSC Code
                    - textbox "Bank IFSC Code" [ref=e252]: RATN0000512
                - generic [ref=e254]:
                  - generic [ref=e255]:
                    - generic [ref=e256]: Bank Branch
                    - generic [ref=e257]: Optional branch name for the GST invoice bank-details panel.
                  - generic [ref=e260]:
                    - generic [ref=e261]: Bank Branch
                    - textbox "Bank Branch" [ref=e263]: Pashan
            - generic [ref=e265]:
              - generic [ref=e266]:
                - generic [ref=e267]: Invoice delivery
                - generic [ref=e268]: Default email and WhatsApp number used when sending invoices to customers.
              - generic [ref=e269]:
                - generic [ref=e270]:
                  - generic [ref=e271]:
                    - generic [ref=e272]: Invoice Email
                    - generic [ref=e273]: Default sender/recipient email address for invoice delivery.
                  - generic [ref=e276]:
                    - generic [ref=e277]: Invoice Email
                    - textbox "Invoice Email" [ref=e279]:
                      - /placeholder: billing@example.com
                - generic [ref=e281]:
                  - generic [ref=e282]:
                    - generic [ref=e283]: WhatsApp Number
                    - generic [ref=e284]: Default WhatsApp number for invoice delivery (include country code).
                  - generic [ref=e287]:
                    - generic [ref=e288]: WhatsApp Number
                    - textbox "WhatsApp Number" [ref=e290]:
                      - /placeholder: "+919405925917"
            - generic [ref=e292]:
              - generic [ref=e293]:
                - generic [ref=e294]: Opening balances
                - generic [ref=e295]: Import a go-live snapshot (trial balance + outstanding party dues) to start ledgers correctly for this company.
              - generic [ref=e296]:
                - generic [ref=e297]:
                  - generic [ref=e300]:
                    - generic [ref=e301]: Go-live (cutover) date
                    - textbox "Go-live (cutover) date" [ref=e303]: 2026-05-31
                  - generic [ref=e305]:
                    - button "Choose CSV" [ref=e306] [cursor=pointer]:
                      - generic [ref=e307]: Choose CSV
                    - generic [ref=e310]: No CSV selected
                - generic [ref=e311]:
                  - button "Download CSV template" [ref=e312] [cursor=pointer]:
                    - generic [ref=e313]: Download CSV template
                  - button "Preview" [disabled]:
                    - generic: Preview
                  - button "Apply (Create Drafts)" [disabled]:
                    - generic: Apply (Create Drafts)
            - generic [ref=e316]:
              - generic [ref=e317]: Changes are written back to the application company record.
              - button "Save Company Details" [ref=e318] [cursor=pointer]:
                - generic [ref=e319]: Save Company Details
        - generic [ref=e322]:
          - generic [ref=e323]:
            - generic [ref=e324]:
              - generic [ref=e325]: UI Access Control
              - generic [ref=e326]: Adjust sidebar and screen visibility per user without changing their base application role.
            - generic [ref=e327]: Role defaults stay intact. Allow/Deny only adds UI overrides.
          - generic [ref=e328]:
            - generic [ref=e329]:
              - button "Administrator admin@example.com Admin" [ref=e330] [cursor=pointer]:
                - generic [ref=e331]: Administrator
                - generic [ref=e332]:
                  - generic [ref=e333]: admin@example.com
                  - generic [ref=e334]: Admin
              - button "Amey Naik amey.naik2012@gmail.com Helper" [ref=e335] [cursor=pointer]:
                - generic [ref=e336]: Amey Naik
                - generic [ref=e337]:
                  - generic [ref=e338]: amey.naik2012@gmail.com
                  - generic [ref=e339]: Helper
              - button "Helper User helper@example.com Helper" [ref=e340] [cursor=pointer]:
                - generic [ref=e341]: Helper User
                - generic [ref=e342]:
                  - generic [ref=e343]: helper@example.com
                  - generic [ref=e344]: Helper
              - button "Shop User shop@example.com Branch" [ref=e345] [cursor=pointer]:
                - generic [ref=e346]: Shop User
                - generic [ref=e347]:
                  - generic [ref=e348]: shop@example.com
                  - generic [ref=e349]: Branch
              - button "Vendor User vendor@example.com Vendor" [ref=e350] [cursor=pointer]:
                - generic [ref=e351]: Vendor User
                - generic [ref=e352]:
                  - generic [ref=e353]: vendor@example.com
                  - generic [ref=e354]: Vendor
            - generic [ref=e355]:
              - generic [ref=e356]:
                - generic [ref=e357]:
                  - generic [ref=e358]: Selected user
                  - generic [ref=e359]: Amey Naik
                  - generic [ref=e360]: amey.naik2012@gmail.com
                - generic [ref=e361]:
                  - generic [ref=e362]:
                    - generic [ref=e363]: Base role
                    - strong [ref=e364]: Helper
                  - generic [ref=e365]:
                    - generic [ref=e366]: Overrides
                    - strong [ref=e367]: "0"
              - generic [ref=e368]:
                - generic [ref=e369]: Default visibility
                - generic [ref=e370]:
                  - generic [ref=e371]: bills.view
                  - generic [ref=e372]: branch_ops.view
                  - generic [ref=e373]: dashboard.view
                  - generic [ref=e374]: orders.view
                  - generic [ref=e375]: reports.view
                  - generic [ref=e376]: stock.view
                  - generic [ref=e377]: user_settings.view
                  - generic [ref=e378]: vendor_ops.view
              - generic [ref=e379]:
                - generic [ref=e380]:
                  - generic [ref=e381]: Workspace
                  - generic [ref=e382]:
                    - generic [ref=e383]:
                      - generic [ref=e384]: Dashboard
                      - generic [ref=e385]: Open the dashboard landing view.
                    - generic [ref=e386]:
                      - generic [ref=e387]: Visible
                      - generic [ref=e388]:
                        - button "Default" [ref=e389] [cursor=pointer]
                        - button "Allow" [ref=e390] [cursor=pointer]
                        - button "Deny" [ref=e391] [cursor=pointer]
                  - generic [ref=e392]:
                    - generic [ref=e393]:
                      - generic [ref=e394]: Orders
                      - generic [ref=e395]: Open order workflow screens.
                    - generic [ref=e396]:
                      - generic [ref=e397]: Visible
                      - generic [ref=e398]:
                        - button "Default" [ref=e399] [cursor=pointer]
                        - button "Allow" [ref=e400] [cursor=pointer]
                        - button "Deny" [ref=e401] [cursor=pointer]
                  - generic [ref=e402]:
                    - generic [ref=e403]:
                      - generic [ref=e404]: Stock
                      - generic [ref=e405]: Open inventory and stock screens.
                    - generic [ref=e406]:
                      - generic [ref=e407]: Visible
                      - generic [ref=e408]:
                        - button "Default" [ref=e409] [cursor=pointer]
                        - button "Allow" [ref=e410] [cursor=pointer]
                        - button "Deny" [ref=e411] [cursor=pointer]
                  - generic [ref=e412]:
                    - generic [ref=e413]:
                      - generic [ref=e414]: Bills & Invoices
                      - generic [ref=e415]: Open billing and invoice screens.
                    - generic [ref=e416]:
                      - generic [ref=e417]: Visible
                      - generic [ref=e418]:
                        - button "Default" [ref=e419] [cursor=pointer]
                        - button "Allow" [ref=e420] [cursor=pointer]
                        - button "Deny" [ref=e421] [cursor=pointer]
                  - generic [ref=e422]:
                    - generic [ref=e423]:
                      - generic [ref=e424]: Reports
                      - generic [ref=e425]: Open reporting and export pages.
                    - generic [ref=e426]:
                      - generic [ref=e427]: Visible
                      - generic [ref=e428]:
                        - button "Default" [ref=e429] [cursor=pointer]
                        - button "Allow" [ref=e430] [cursor=pointer]
                        - button "Deny" [ref=e431] [cursor=pointer]
                - generic [ref=e432]:
                  - generic [ref=e433]: Operations
                  - generic [ref=e434]:
                    - generic [ref=e435]:
                      - generic [ref=e436]: Vendor Operations
                      - generic [ref=e437]: Open vendor operations queues.
                    - generic [ref=e438]:
                      - generic [ref=e439]: Visible
                      - generic [ref=e440]:
                        - button "Default" [ref=e441] [cursor=pointer]
                        - button "Allow" [ref=e442] [cursor=pointer]
                        - button "Deny" [ref=e443] [cursor=pointer]
                  - generic [ref=e444]:
                    - generic [ref=e445]:
                      - generic [ref=e446]: Branch Operations
                      - generic [ref=e447]: Open branch operations queues.
                    - generic [ref=e448]:
                      - generic [ref=e449]: Visible
                      - generic [ref=e450]:
                        - button "Default" [ref=e451] [cursor=pointer]
                        - button "Allow" [ref=e452] [cursor=pointer]
                        - button "Deny" [ref=e453] [cursor=pointer]
                - generic [ref=e454]:
                  - generic [ref=e455]: Administration
                  - generic [ref=e456]:
                    - generic [ref=e457]:
                      - generic [ref=e458]: Master Data
                      - generic [ref=e459]: Open vendors, branches, categories, and items.
                    - generic [ref=e460]:
                      - generic [ref=e461]: Hidden
                      - generic [ref=e462]:
                        - button "Default" [ref=e463] [cursor=pointer]
                        - button "Allow" [ref=e464] [cursor=pointer]
                        - button "Deny" [ref=e465] [cursor=pointer]
                  - generic [ref=e466]:
                    - generic [ref=e467]:
                      - generic [ref=e468]: Master Data Review
                      - generic [ref=e469]: Open the master data review queue.
                    - generic [ref=e470]:
                      - generic [ref=e471]: Hidden
                      - generic [ref=e472]:
                        - button "Default" [ref=e473] [cursor=pointer]
                        - button "Allow" [ref=e474] [cursor=pointer]
                        - button "Deny" [ref=e475] [cursor=pointer]
                  - generic [ref=e476]:
                    - generic [ref=e477]:
                      - generic [ref=e478]: Bill Review
                      - generic [ref=e479]: Review and approve recorded payments.
                    - generic [ref=e480]:
                      - generic [ref=e481]: Hidden
                      - generic [ref=e482]:
                        - button "Default" [ref=e483] [cursor=pointer]
                        - button "Allow" [ref=e484] [cursor=pointer]
                        - button "Deny" [ref=e485] [cursor=pointer]
                  - generic [ref=e486]:
                    - generic [ref=e487]:
                      - generic [ref=e488]: Company Settings
                      - generic [ref=e489]: Open company settings and admin controls.
                    - generic [ref=e490]:
                      - generic [ref=e491]: Hidden
                      - generic [ref=e492]:
                        - button "Default" [ref=e493] [cursor=pointer]
                        - button "Allow" [ref=e494] [cursor=pointer]
                        - button "Deny" [ref=e495] [cursor=pointer]
                - generic [ref=e496]:
                  - generic [ref=e497]: Profile
                  - generic [ref=e498]:
                    - generic [ref=e499]:
                      - generic [ref=e500]: User Settings
                      - generic [ref=e501]: Open the personal user details screen.
                    - generic [ref=e502]:
                      - generic [ref=e503]: Visible
                      - generic [ref=e504]:
                        - button "Default" [ref=e505] [cursor=pointer]
                        - button "Allow" [ref=e506] [cursor=pointer]
                        - button "Deny" [ref=e507] [cursor=pointer]
              - generic [ref=e508]:
                - generic [ref=e509]: These overrides affect shell navigation and guarded screens after the next profile refresh.
                - generic [ref=e510]:
                  - button "Reset overrides" [ref=e511] [cursor=pointer]:
                    - generic [ref=e512]: Reset overrides
                  - button "Save Access Rules" [ref=e515] [cursor=pointer]:
                    - generic [ref=e516]: Save Access Rules
```

# Test source

```ts
  1  | import { expect, test } from '@playwright/test';
  2  | 
  3  | test('Company logo loads on Company Settings', async ({ page }) => {
  4  |   const requestFailures: string[] = [];
  5  |   const debug: string[] = [];
  6  | 
  7  |   page.on('requestfailed', request => {
  8  |     const url = request.url();
  9  |     if (request.resourceType() === 'image' || url.includes('/files/')) {
  10 |       requestFailures.push(`${request.failure()?.errorText ?? 'FAILED'} ${url}`);
  11 |     }
  12 |   });
  13 | 
  14 |   try {
  15 |     await page.goto('/login?returnUrl=/company-settings');
  16 | 
  17 |     await page.getByRole('textbox', { name: 'Username' }).fill(process.env.PLAYWRIGHT_USERNAME ?? 'Administrator');
  18 |     await page.getByRole('textbox', { name: 'Password' }).fill(process.env.PLAYWRIGHT_PASSWORD ?? 'admin');
  19 |     await page.getByRole('button', { name: 'Sign in' }).click();
  20 |     await page.waitForURL(/\/company-settings$/);
  21 | 
  22 |     const logo = page.locator('img.logo-preview-image');
  23 |     await expect(logo).toBeVisible();
  24 | 
  25 |     const src = (await logo.getAttribute('src')) ?? '';
  26 |     debug.push(`logo.src=${src || '<empty>'}`);
  27 |     expect(src).toBeTruthy();
  28 |     expect(src).not.toMatch(/^https?:\/\/localhost:8080/i);
  29 | 
  30 |     const resolvedUrl = new URL(src, page.url()).toString();
  31 |     debug.push(`logo.resolvedUrl=${resolvedUrl}`);
  32 | 
  33 |     const apiResponse = await page.request.get(resolvedUrl);
  34 |     debug.push(`logo.fetchStatus=${apiResponse.status()}`);
  35 |     debug.push(`logo.fetchContentType=${apiResponse.headers()['content-type'] ?? '<none>'}`);
  36 | 
  37 |     // Ensure the browser actually decoded the image (not a broken 404/HTML response).
  38 |     await logo.evaluate(img => new Promise<void>(resolve => {
  39 |       const el = img as HTMLImageElement;
  40 |       if (el.complete) {
  41 |         resolve();
  42 |         return;
  43 |       }
  44 |       el.addEventListener('load', () => resolve(), { once: true });
  45 |       el.addEventListener('error', () => resolve(), { once: true });
  46 |     }));
  47 | 
  48 |     const naturalWidth = await logo.evaluate(img => (img as HTMLImageElement).naturalWidth);
  49 |     debug.push(`logo.naturalWidth=${naturalWidth}`);
> 50 |     expect(naturalWidth).toBeGreaterThan(0);
     |                          ^ Error: expect(received).toBeGreaterThan(expected)
  51 |   } finally {
  52 |     if (debug.length) {
  53 |       test.info().annotations.push({ type: 'debug', description: debug.join('\n') });
  54 |     }
  55 |     if (requestFailures.length) {
  56 |       test.info().annotations.push({ type: 'image-request-failures', description: requestFailures.join('\n') });
  57 |     }
  58 |   }
  59 | });
  60 | 
```