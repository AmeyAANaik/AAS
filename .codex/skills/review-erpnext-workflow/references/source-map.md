# Source Map

Use this file to jump directly to workflow-critical code and docs.

## Core Context

- `PROJECT_CONTEXT.md`
- `docs/system-architecture-analysis.md`
- `docs/erpnext-workflow-review.md`

## Middleware Workflow Files

- `mw/src/main/java/com/aas/mw/controller/OrdersController.java`
- `mw/src/main/java/com/aas/mw/controller/VendorAssignmentController.java`
- `mw/src/main/java/com/aas/mw/controller/InvoiceController.java`
- `mw/src/main/java/com/aas/mw/controller/PaymentsController.java`
- `mw/src/main/java/com/aas/mw/service/OrderService.java`
- `mw/src/main/java/com/aas/mw/service/OrderFlowStateMachine.java`
- `mw/src/main/java/com/aas/mw/service/VendorAssignmentService.java`
- `mw/src/main/java/com/aas/mw/service/VendorPdfService.java`
- `mw/src/main/java/com/aas/mw/service/OrderBillingService.java`
- `mw/src/main/java/com/aas/mw/service/PaymentService.java`
- `mw/src/main/java/com/aas/mw/service/SetupService.java`
- `mw/src/main/java/com/aas/mw/client/ErpNextClient.java`

## UI Workflow Files

- `ui/src/app/orders/order.service.ts`
- `ui/src/app/orders/order-page/order-page.component.ts`
- `ui/src/app/orders/order-create/order-create.component.ts`
- `ui/src/app/bills/bills.service.ts`
- `ui/src/app/bills/bills-page/bills-page.component.ts`
- `ui/src/app/bills/invoice-create/invoice-create.component.ts`
- `ui/src/app/bills/payment-form/payment-form.component.ts`

## Useful Search Patterns

- `rg "aas_status|assign-vendor|vendor-bill|sell-order|branch-image" mw ui docs`
- `rg "Purchase Invoice|Sales Invoice|Payment Entry|Sales Order" mw/src/main/java`
- `rg "ERPNext|workflow|state machine|margin" docs mw ui`
