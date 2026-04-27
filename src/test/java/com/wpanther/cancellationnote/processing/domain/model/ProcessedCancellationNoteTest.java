package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedCancellationNoteTest {

    private static final CancellationNoteId ID = new CancellationNoteId(UUID.randomUUID());
    private static final LocalDate ISSUE_DATE = LocalDate.of(2025, 1, 15);
    private static final LocalDate CANCELLATION_DATE = LocalDate.of(2025, 2, 1);
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("100.00"), "THB");
    private static final LineItem ITEM_1 = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
    private static final LineItem ITEM_2 = new LineItem("Gadget", 1, Money.of(new BigDecimal("50.00"), "THB"), new BigDecimal("7.00"));
    private static final Party SELLER = new Party(
            "Seller Corp",
            new TaxIdentifier("1234567890", "VAT"),
            new Address("100 Seller St", "Bangkok", "10100", "TH"),
            "seller@example.com"
    );
    private static final Party BUYER = new Party(
            "Buyer Corp",
            new TaxIdentifier("0987654321", "VAT"),
            new Address("200 Buyer St", "Bangkok", "10200", "TH"),
            "buyer@example.com"
    );

    private ProcessedCancellationNote validNote() {
        return ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(ITEM_1, ITEM_2))
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build();
    }

    // --- Builder and accessor tests ---

    @Test
    void buildWithAllRequiredFields() {
        ProcessedCancellationNote note = validNote();
        assertEquals(ID, note.id());
        assertEquals("SRC-001", note.sourceNoteId());
        assertEquals("CN-001", note.cancellationNoteNumber());
        assertEquals(ISSUE_DATE, note.issueDate());
        assertEquals(CANCELLATION_DATE, note.cancellationDate());
        assertEquals(SELLER, note.seller());
        assertEquals(BUYER, note.buyer());
        assertEquals("THB", note.currency());
        assertEquals("INV-001", note.cancelledInvoiceNumber());
        assertEquals("<xml/>", note.originalXml());
    }

    @Test
    void defaultStatusIsPending() {
        ProcessedCancellationNote note = validNote();
        assertEquals(ProcessingStatus.PENDING, note.status());
    }

    @Test
    void defaultCreatedAtIsSet() {
        ProcessedCancellationNote note = validNote();
        assertNotNull(note.createdAt());
        assertTrue(note.createdAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void explicitStatusOverridesDefault() {
        ProcessedCancellationNote note = ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(ITEM_1))
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .status(ProcessingStatus.PROCESSING)
                .build();
        assertEquals(ProcessingStatus.PROCESSING, note.status());
    }

    // --- Validation tests ---

    @Test
    void nullIdThrowsException() {
        assertThrows(NullPointerException.class, () -> ProcessedCancellationNote.builder()
                .id(null)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(ITEM_1))
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build());
    }

    @Test
    void nullSourceNoteIdThrowsException() {
        assertThrows(NullPointerException.class, () -> ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId(null)
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(ITEM_1))
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build());
    }

    @Test
    void emptyItemsThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of())
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build());
        assertEquals("Cancellation note must have at least one line item", ex.getMessage());
    }

    @Test
    void cancellationDateBeforeIssueDateThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(LocalDate.of(2025, 3, 1))
                .cancellationDate(LocalDate.of(2025, 2, 1))
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(ITEM_1))
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build());
        assertEquals("Cancellation date cannot be before issue date", ex.getMessage());
    }

    @Test
    void invalidCurrencyLengthThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(ITEM_1))
                .currency("TH")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build());
        assertEquals("Currency must be a 3-letter ISO code", ex.getMessage());
    }

    @Test
    void mismatchedItemCurrencyThrowsException() {
        LineItem usdItem = new LineItem("Widget", 1, Money.of(BigDecimal.TEN, "USD"), BigDecimal.ZERO);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ProcessedCancellationNote.builder()
                .id(ID)
                .sourceNoteId("SRC-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(ISSUE_DATE)
                .cancellationDate(CANCELLATION_DATE)
                .seller(SELLER)
                .buyer(BUYER)
                .items(List.of(usdItem))
                .currency("THB")
                .cancelledInvoiceNumber("INV-001")
                .originalXml("<xml/>")
                .build());
        assertTrue(ex.getMessage().contains("does not match note currency"));
    }

    // --- Items immutability ---

    @Test
    void itemsReturnsUnmodifiableList() {
        ProcessedCancellationNote note = validNote();
        assertThrows(UnsupportedOperationException.class,
                () -> note.items().add(new LineItem("Extra", 1, UNIT_PRICE, BigDecimal.ZERO)));
    }

    // --- Totals calculation tests ---

    @Test
    void getSubtotalSumsLineTotals() {
        ProcessedCancellationNote note = validNote();
        // Item_1: 2 * 100.00 = 200.00; Item_2: 1 * 50.00 = 50.00
        Money subtotal = note.getSubtotal();
        assertEquals(new BigDecimal("250.00"), subtotal.amount());
    }

    @Test
    void getTotalTaxSumsTaxAmounts() {
        ProcessedCancellationNote note = validNote();
        // Item_1 tax: (200.00 * 7) / 100 = 14.00; Item_2 tax: (50.00 * 7) / 100 = 3.50
        Money totalTax = note.getTotalTax();
        assertEquals(new BigDecimal("17.50"), totalTax.amount());
    }

    @Test
    void getTotalSumsTotalWithTax() {
        ProcessedCancellationNote note = validNote();
        // Item_1 total with tax: 214.00; Item_2 total with tax: 53.50
        Money total = note.getTotal();
        assertEquals(new BigDecimal("267.50"), total.amount());
    }

    @Test
    void totalEqualsSubtotalPlusTotalTax() {
        ProcessedCancellationNote note = validNote();
        Money expected = note.getSubtotal().add(note.getTotalTax());
        assertEquals(expected.amount(), note.getTotal().amount());
    }

    // --- Status transition tests ---

    @Test
    void markProcessingTransitionsToProcessing() {
        ProcessedCancellationNote note = validNote();
        note.markProcessing();
        assertEquals(ProcessingStatus.PROCESSING, note.status());
        assertNull(note.completedAt());
        assertNull(note.errorMessage());
    }

    @Test
    void markCompletedTransitionsToCompleted() {
        ProcessedCancellationNote note = validNote();
        note.markCompleted();
        assertEquals(ProcessingStatus.COMPLETED, note.status());
        assertNotNull(note.completedAt());
        assertNull(note.errorMessage());
    }

    @Test
    void markFailedTransitionsToFailed() {
        ProcessedCancellationNote note = validNote();
        note.markFailed("Processing error");
        assertEquals(ProcessingStatus.FAILED, note.status());
        assertNotNull(note.completedAt());
        assertEquals("Processing error", note.errorMessage());
    }

    @Test
    void markProcessingClearsPreviousCompletedAtAndError() {
        ProcessedCancellationNote note = validNote();
        note.markFailed("some error");
        note.markProcessing();
        assertNull(note.completedAt());
        assertNull(note.errorMessage());
    }

    @Test
    void fullStatusLifecycle() {
        ProcessedCancellationNote note = validNote();
        assertEquals(ProcessingStatus.PENDING, note.status());

        note.markProcessing();
        assertEquals(ProcessingStatus.PROCESSING, note.status());

        note.markCompleted();
        assertEquals(ProcessingStatus.COMPLETED, note.status());
    }
}
