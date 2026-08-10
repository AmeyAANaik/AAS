package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Receivables aging / defaulter reporting. All cases pin an explicit {@code asOf} so the
 * expectations cannot rot as real time moves past the fixture dates.
 */
@ExtendWith(MockitoExtension.class)
class BranchOpsServiceAgingTest {

    private static final String AS_OF = "2026-08-02";
    private static final String BRANCH = "BRANCH-1";

    @Mock
    private ErpNextClient erpNextClient;
    @Mock
    private AdjustmentNoteErpService adjustmentNoteErpService;

    @InjectMocks
    private BranchOpsService branchOpsService;

    @BeforeEach
    void setup() {
        lenient().when(erpNextClient.listResources(eq("Journal Entry"), anyMap())).thenReturn(List.of());
        lenient().when(erpNextClient.listResources(eq("Sales Order"), anyMap())).thenReturn(List.of());
        lenient().when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());
        lenient().when(erpNextClient.listResources(eq("Payment Entry Reference"), anyMap())).thenReturn(List.of());
    }

    // ---- fixtures -------------------------------------------------------

    @SafeVarargs
    private void stubBranches(Map<String, Object>... branches) {
        lenient().when(erpNextClient.listResources(eq("Customer"), anyMap())).thenReturn(List.of(branches));
        for (Map<String, Object> branch : branches) {
            lenient().when(erpNextClient.getResource("Customer", String.valueOf(branch.get("name"))))
                    .thenReturn(Map.of("data", branch));
        }
    }

    @SafeVarargs
    private void stubInvoices(Map<String, Object>... invoices) {
        lenient().when(erpNextClient.listResources(eq("Sales Invoice"), anyMap())).thenReturn(List.of(invoices));
    }

    private Map<String, Object> branch(String name, int creditDays) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("customer_name", name);
        row.put("aas_branch_location", "Pune");
        row.put("aas_credit_days", creditDays);
        row.put("modified", "2026-08-01 10:00:00");
        return row;
    }

    /** Submitted invoice with an explicit ERP due date. */
    private Map<String, Object> submitted(String name, String postingDate, String dueDate, double grandTotal, double outstanding) {
        Map<String, Object> row = invoiceBase(name, postingDate, grandTotal, outstanding);
        row.put("due_date", dueDate);
        row.put("docstatus", 1);
        row.put("status", outstanding > 0 ? "Overdue" : "Paid");
        return row;
    }

    private Map<String, Object> draft(String name, String postingDate, double grandTotal) {
        Map<String, Object> row = invoiceBase(name, postingDate, grandTotal, grandTotal);
        row.put("docstatus", 0);
        row.put("status", "Draft");
        return row;
    }

    private Map<String, Object> invoiceBase(String name, String postingDate, double grandTotal, double outstanding) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("customer", BRANCH);
        row.put("posting_date", postingDate);
        row.put("grand_total", grandTotal);
        row.put("rounded_total", grandTotal);
        row.put("outstanding_amount", outstanding);
        row.put("modified", postingDate + " 10:00:00");
        row.put("creation", postingDate + " 09:00:00");
        return row;
    }

    private Map<String, Object> payment(String name, String postingDate, double amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("party", BRANCH);
        row.put("party_type", "Customer");
        row.put("posting_date", postingDate);
        row.put("paid_amount", amount);
        row.put("received_amount", amount);
        row.put("docstatus", 1);
        row.put("modified", postingDate + " 10:00:00");
        return row;
    }

    private Map<String, Object> reference(String paymentId, String invoiceId, double allocated) {
        return Map.of("parent", paymentId, "reference_name", invoiceId, "allocated_amount", allocated);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> onlyBranchRow(Map<String, Object> summary) {
        List<Map<String, Object>> branches = (List<Map<String, Object>>) summary.get("branches");
        assertThat(branches).hasSize(1);
        return branches.get(0);
    }

    @SafeVarargs
    private Map<String, Object> agingFor(Map<String, Object>... invoices) {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(invoices);
        return onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));
    }

    // ---- bucketing ------------------------------------------------------

    @Test
    void bucketsSubmittedInvoicesByDaysPastDue() {
        // due dates chosen so daysPastDue is exactly -1, 0, 1, 7, 8, 15, 16, 30, 31 as of 2026-08-02
        Map<String, Object> row = agingFor(
                submitted("SI-M1", "2026-07-01", "2026-08-03", 100, 100),  // -1 -> notDue
                submitted("SI-00", "2026-07-01", "2026-08-02", 100, 100),  //  0 -> notDue
                submitted("SI-01", "2026-07-01", "2026-08-01", 100, 100),  //  1 -> d1_7
                submitted("SI-07", "2026-07-01", "2026-07-26", 100, 100),  //  7 -> d1_7
                submitted("SI-08", "2026-07-01", "2026-07-25", 100, 100),  //  8 -> d8_15
                submitted("SI-15", "2026-07-01", "2026-07-18", 100, 100),  // 15 -> d8_15
                submitted("SI-16", "2026-07-01", "2026-07-17", 100, 100),  // 16 -> d16_30
                submitted("SI-30", "2026-07-01", "2026-07-03", 100, 100),  // 30 -> d16_30
                submitted("SI-31", "2026-07-01", "2026-07-02", 100, 100)); // 31 -> d30Plus

        assertThat(row.get("notDue")).isEqualTo(200.0);
        assertThat(row.get("d1_7")).isEqualTo(200.0);
        assertThat(row.get("d8_15")).isEqualTo(200.0);
        assertThat(row.get("d16_30")).isEqualTo(200.0);
        assertThat(row.get("d30Plus")).isEqualTo(100.0);
        assertThat(row.get("submittedOutstanding")).isEqualTo(900.0);
        assertThat(row.get("overdueAmount")).isEqualTo(700.0);
    }

    @Test
    void invoiceDueExactlyOnAsOfDateIsNotYetOverdue() {
        Map<String, Object> row = agingFor(submitted("SI-1", "2026-07-26", AS_OF, 5000, 5000));

        assertThat(row.get("notDue")).isEqualTo(5000.0);
        assertThat(row.get("overdueAmount")).isEqualTo(0.0);
        assertThat(row.get("oldestOverdueDays")).isEqualTo(0);
        assertThat(row.get("overdueInvoiceCount")).isEqualTo(0);
    }

    @Test
    void excludesDraftsFromBucketsAndReportsThemSeparately() {
        Map<String, Object> row = agingFor(
                submitted("SI-1", "2026-06-20", "2026-06-27", 20000, 20000),
                draft("SI-D1", "2026-07-28", 15000),
                draft("SI-D2", "2026-07-30", 5000));

        assertThat(row.get("submittedOutstanding")).isEqualTo(20000.0);
        assertThat(row.get("d30Plus")).isEqualTo(20000.0);
        assertThat(row.get("draftUnbilledAmount")).isEqualTo(20000.0);
        assertThat(row.get("draftInvoiceCount")).isEqualTo(2);
        assertThat(row.get("oldestDraftDays")).isEqualTo(5); // 2026-07-28 -> 2026-08-02
    }

    @Test
    void branchWithOnlyDraftsIsNeverADefaulter() {
        // Mirrors the live "Sukarta Aundh" case: large unbilled backlog, nothing actually overdue.
        Map<String, Object> row = agingFor(
                draft("SI-D1", "2026-06-05", 400000),
                draft("SI-D2", "2026-06-10", 308796));

        assertThat(row.get("overdueAmount")).isEqualTo(0.0);
        assertThat(row.get("submittedOutstanding")).isEqualTo(0.0);
        assertThat(row.get("draftUnbilledAmount")).isEqualTo(708796.0);
        assertThat(row.get("riskTier")).isEqualTo("GOOD");
        assertThat(row.get("oldestOverdueDays")).isEqualTo(0);
    }

    @Test
    void partiallyPaidInvoiceAgesOnlyItsOutstandingAmount() {
        Map<String, Object> row = agingFor(submitted("SI-1", "2026-06-20", "2026-06-27", 25000, 20000));

        assertThat(row.get("d30Plus")).isEqualTo(20000.0);
        assertThat(row.get("submittedOutstanding")).isEqualTo(20000.0);
        assertThat(row.get("openInvoiceCount")).isEqualTo(1);
    }

    @Test
    void settledInvoiceBelowToleranceIsExcludedFromBucketsButCountsTowardOnTimeDenominator() {
        Map<String, Object> row = agingFor(submitted("SI-1", "2026-06-20", "2026-06-27", 25000, 0.4));

        assertThat(row.get("submittedOutstanding")).isEqualTo(0.0);
        assertThat(row.get("openInvoiceCount")).isEqualTo(0);
        assertThat(row.get("onTimePaymentDenominator")).isEqualTo(1);
    }

    // ---- credit days / due date derivation ------------------------------

    @Test
    void creditDaysZeroUsesPostingDateAsDueDate() {
        // The "Sukarta Aundh" configuration: 0 credit days must not blow up and must age from posting date.
        stubBranches(branch(BRANCH, 0));
        Map<String, Object> invoice = invoiceBase("SI-1", "2026-07-30", 9000, 9000);
        invoice.put("docstatus", 1);
        invoice.put("status", "Overdue");
        stubInvoices(invoice);

        Map<String, Object> row = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));

        assertThat(row.get("d1_7")).isEqualTo(9000.0);
        assertThat(row.get("oldestOverdueDays")).isEqualTo(3);
    }

    @Test
    void missingDueDateFallsBackToPostingDatePlusCreditDays() {
        stubBranches(branch(BRANCH, 7));
        Map<String, Object> invoice = invoiceBase("SI-1", "2026-07-20", 4000, 4000);
        invoice.put("docstatus", 1);
        invoice.put("status", "Overdue");
        stubInvoices(invoice);
        lenient().when(erpNextClient.getResource("Customer", BRANCH))
                .thenReturn(Map.of("data", branch(BRANCH, 7)));

        Map<String, Object> detail = branchOpsService.getBranchAging(BRANCH, AS_OF, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoices = (List<Map<String, Object>>) detail.get("invoices");

        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).get("dueDateSource")).isEqualTo("DERIVED");
        assertThat(invoices.get(0).get("dueDate")).isEqualTo("2026-07-27"); // posting + 7
        assertThat(invoices.get(0).get("daysPastDue")).isEqualTo(6);
    }

    @Test
    void invoiceWithNoDatesAtAllIsCountedAsMissingAndTreatedAsNotDue() {
        stubBranches(branch(BRANCH, 7));
        Map<String, Object> invoice = invoiceBase("SI-1", "", 4000, 4000);
        invoice.put("docstatus", 1);
        invoice.put("status", "Overdue");
        stubInvoices(invoice);

        Map<String, Object> row = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));

        assertThat(row.get("dueDateMissingCount")).isEqualTo(1);
        assertThat(row.get("notDue")).isEqualTo(4000.0);
        assertThat(row.get("overdueAmount")).isEqualTo(0.0);
    }

    // ---- on-time payment coverage ---------------------------------------

    @Test
    void noPaymentsYieldsNullOnTimePctAndAgingOnlyRiskBasis() {
        Map<String, Object> row = agingFor(submitted("SI-1", "2026-06-20", "2026-06-27", 25000, 25000));

        assertThat(row.get("onTimePaymentPct")).isNull();
        assertThat(row.get("onTimePaymentSample")).isEqualTo(0);
        assertThat(row.get("onTimeReliable")).isEqualTo(false);
        assertThat(row.get("riskBasis")).isEqualTo("AGING_ONLY");
        assertThat(row.get("riskScoreMax")).isEqualTo(6);
    }

    @Test
    void onTimePctUsesOnlyReferencedPaymentsAndReportsCoverage() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(
                submitted("SI-1", "2026-06-20", "2026-06-27", 10000, 0),
                submitted("SI-2", "2026-06-21", "2026-06-28", 10000, 0),
                submitted("SI-3", "2026-06-22", "2026-06-29", 10000, 0));
        // Only SI-1 has a payment reference row, and it settled before its due date.
        lenient().when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(payment("PAY-1", "2026-06-25", 10000)));
        lenient().when(erpNextClient.listResources(eq("Payment Entry Reference"), anyMap()))
                .thenReturn(List.of(reference("PAY-1", "SI-1", 10000)));

        Map<String, Object> row = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));

        assertThat(row.get("onTimePaymentDenominator")).isEqualTo(3);
        assertThat(row.get("onTimePaymentSample")).isEqualTo(1);
        assertThat(row.get("onTimeCoveragePct")).isEqualTo(33.33);
        assertThat(row.get("onTimeReliable")).isEqualTo(false);
        assertThat(row.get("onTimePaymentPct")).isEqualTo(100.0);
    }

    @Test
    void lateSettlementCountsAgainstOnTimePct() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(
                submitted("SI-1", "2026-06-20", "2026-06-27", 10000, 0),
                submitted("SI-2", "2026-06-21", "2026-06-28", 10000, 0));
        lenient().when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        payment("PAY-1", "2026-06-26", 10000),   // on time
                        payment("PAY-2", "2026-07-15", 10000))); // late
        lenient().when(erpNextClient.listResources(eq("Payment Entry Reference"), anyMap()))
                .thenReturn(List.of(
                        reference("PAY-1", "SI-1", 10000),
                        reference("PAY-2", "SI-2", 10000)));

        Map<String, Object> row = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));

        assertThat(row.get("onTimePaymentSample")).isEqualTo(2);
        assertThat(row.get("onTimeCoveragePct")).isEqualTo(100.0);
        assertThat(row.get("onTimeReliable")).isEqualTo(true);
        assertThat(row.get("onTimePaymentPct")).isEqualTo(50.0);
    }

    @Test
    void paymentEntryReferenceQueryFailureDegradesGracefully() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(submitted("SI-1", "2026-06-20", "2026-06-27", 25000, 25000));
        lenient().when(erpNextClient.listResources(eq("Payment Entry Reference"), anyMap()))
                .thenThrow(new RuntimeException("Field not permitted in query"));

        Map<String, Object> row = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));

        assertThat(row.get("onTimeReliable")).isEqualTo(false);
        assertThat(row.get("d30Plus")).isEqualTo(25000.0);
        assertThat(row.get("riskBasis")).isEqualTo("AGING_ONLY");
    }

    // ---- risk tiering ---------------------------------------------------

    @ParameterizedTest(name = "{0} days overdue, amount {1} -> {2}")
    @CsvSource({
            "0,     0,     GOOD",       // nothing overdue
            "2,     300,   GOOD",       // trivial, below the flag threshold
            "10,    15000, WATCH",      // 1 age pt + 1 amount pt of 6
            "51,    52353, DEFAULTER",  // live Kothrud shape: hard defaulter on age
            "46,    600,   DEFAULTER"   // hard defaulter purely on age
    })
    void classifiesReceivableRiskAcrossThresholdBoundaries(int daysOverdue, double amount, String expectedTier) {
        stubBranches(branch(BRANCH, 7));
        if (amount <= 0) {
            stubInvoices(draft("SI-D1", "2026-07-30", 5000));
        } else {
            String dueDate = java.time.LocalDate.parse(AS_OF).minusDays(daysOverdue).toString();
            stubInvoices(submitted("SI-1", "2026-06-01", dueDate, amount, amount));
        }

        Map<String, Object> row = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));

        assertThat(row.get("riskTier")).isEqualTo(expectedTier);
    }

    @Test
    void riskReasonsExplainTheTier() {
        Map<String, Object> row = agingFor(submitted("SI-1", "2026-06-01", "2026-06-12", 52353, 52353));

        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) row.get("riskReasons");
        assertThat(reasons).anyMatch(reason -> reason.startsWith("Oldest overdue"));
        assertThat(reasons).anyMatch(reason -> reason.startsWith("Overdue"));
    }

    // ---- reconciliation, ranking, performance ---------------------------

    @Test
    void reconciliationIdentityHoldsAndMatchesTheLedgerBalance() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(
                submitted("SI-1", "2026-06-20", "2026-06-27", 20000, 20000),
                draft("SI-D1", "2026-07-28", 15000));

        Map<String, Object> detail = branchOpsService.getBranchAging(BRANCH, AS_OF, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> reconciliation = (Map<String, Object>) detail.get("reconciliation");

        double submitted = (double) reconciliation.get("submittedOutstanding");
        double draft = (double) reconciliation.get("draftUnbilled");
        double unapplied = (double) reconciliation.get("unappliedCredits");
        double ledger = (double) reconciliation.get("ledgerBalance");

        assertThat(reconciliation.get("balanced")).isEqualTo(true);
        assertThat(submitted + draft - unapplied).isCloseTo(ledger, org.assertj.core.api.Assertions.within(0.5));
        // Ledger balance must equal what the existing Ledger tab reports for the same fixtures.
        assertThat(ledger).isEqualTo(35000.0);
    }

    @Test
    void agingNeverFetchesIndividualPaymentEntryDocuments() {
        // Regression lock: the ledger path is N+1 on Payment Entry; the aging path must not be.
        stubBranches(branch(BRANCH, 7));
        stubInvoices(submitted("SI-1", "2026-06-20", "2026-06-27", 20000, 20000));
        lenient().when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(payment("PAY-1", "2026-06-25", 5000)));
        lenient().when(erpNextClient.listResources(eq("Payment Entry Reference"), anyMap()))
                .thenReturn(List.of(reference("PAY-1", "SI-1", 5000)));

        branchOpsService.getAgingSummary(AS_OF, null, null);

        verify(erpNextClient, never()).getResource(eq("Payment Entry"), anyString());
    }

    @Test
    void ranksCollectionsRiskAndBillingBacklogIndependently() {
        // The live inversion: the branch with the least overdue carries the biggest unbilled backlog.
        Map<String, Object> kothrud = branch("KOTHRUD", 7);
        Map<String, Object> sinhgad = branch("SINHGAD", 7);
        lenient().when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenReturn(List.of(kothrud, sinhgad));

        List<Map<String, Object>> invoices = new ArrayList<>();
        Map<String, Object> kothrudOverdue = submitted("SI-K1", "2026-06-01", "2026-06-12", 52353, 52353);
        kothrudOverdue.put("customer", "KOTHRUD");
        Map<String, Object> kothrudDraft = draft("SI-KD1", "2026-06-01", 909186);
        kothrudDraft.put("customer", "KOTHRUD");
        Map<String, Object> sinhgadOverdue = submitted("SI-S1", "2026-06-01", "2026-06-10", 296762, 296762);
        sinhgadOverdue.put("customer", "SINHGAD");
        Map<String, Object> sinhgadDraft = draft("SI-SD1", "2026-06-01", 354330);
        sinhgadDraft.put("customer", "SINHGAD");
        invoices.add(kothrudOverdue);
        invoices.add(kothrudDraft);
        invoices.add(sinhgadOverdue);
        invoices.add(sinhgadDraft);
        lenient().when(erpNextClient.listResources(eq("Sales Invoice"), anyMap())).thenReturn(invoices);

        Map<String, Object> summary = branchOpsService.getAgingSummary(AS_OF, null, null);

        assertThat(summary.get("collectionsRanking")).isEqualTo(List.of("SINHGAD", "KOTHRUD"));
        assertThat(summary.get("backlogRanking")).isEqualTo(List.of("KOTHRUD", "SINHGAD"));
    }

    @Test
    void asOfDefaultsToTodayAndFutureDatesAreClamped() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(submitted("SI-1", "2026-06-20", "2026-06-27", 20000, 20000));

        Map<String, Object> today = branchOpsService.getAgingSummary(null, null, null);
        Map<String, Object> future = branchOpsService.getAgingSummary("2099-01-01", null, null);

        assertThat(today.get("asOfDate")).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(future.get("asOfDate")).isEqualTo(java.time.LocalDate.now().toString());
    }

    @Test
    void postingDateRangeFiltersInvoicesWithoutShiftingBuckets() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(
                submitted("SI-OLD", "2026-06-01", "2026-06-08", 10000, 10000),
                submitted("SI-NEW", "2026-07-20", "2026-07-27", 4000, 4000));

        Map<String, Object> unfiltered = onlyBranchRow(branchOpsService.getAgingSummary(AS_OF, null, null));
        Map<String, Object> filtered = onlyBranchRow(
                branchOpsService.getAgingSummary(AS_OF, "2026-07-01", "2026-07-31"));

        assertThat(unfiltered.get("submittedOutstanding")).isEqualTo(14000.0);
        // The surviving invoice keeps the same bucket it had in the unfiltered run.
        assertThat(unfiltered.get("d1_7")).isEqualTo(4000.0);
        assertThat(filtered.get("submittedOutstanding")).isEqualTo(4000.0);
        assertThat(filtered.get("d1_7")).isEqualTo(4000.0);
    }

    @Test
    void branchDetailOrdersOverdueInvoicesFirstAndDraftsLast() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(
                draft("SI-D1", "2026-07-28", 15000),
                submitted("SI-NOTDUE", "2026-07-30", "2026-08-06", 3000, 3000),
                submitted("SI-OLD", "2026-06-01", "2026-06-08", 10000, 10000));

        Map<String, Object> detail = branchOpsService.getBranchAging(BRANCH, AS_OF, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoices = (List<Map<String, Object>>) detail.get("invoices");

        assertThat(invoices.stream().map(row -> row.get("invoiceId")).toList())
                .containsExactly("SI-OLD", "SI-NOTDUE", "SI-D1");
        assertThat(invoices.get(2).get("bucketLabel")).isEqualTo("Draft / unbilled");
    }

    @Test
    void exportRowsAreFlatAndLabelled() {
        stubBranches(branch(BRANCH, 7));
        stubInvoices(submitted("SI-1", "2026-06-01", "2026-06-12", 52353, 52353));

        List<Map<String, Object>> rows = branchOpsService.getAgingSummaryRows(AS_OF, null, null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsKeys("Branch", "Credit Days", "30+ days", "Overdue", "Draft / Unbilled", "Risk Tier");
        assertThat(rows.get(0).values()).noneMatch(value -> value instanceof Map || value instanceof List);
    }
}
