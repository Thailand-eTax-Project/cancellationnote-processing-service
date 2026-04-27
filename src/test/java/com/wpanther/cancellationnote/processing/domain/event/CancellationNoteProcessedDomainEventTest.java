package com.wpanther.cancellationnote.processing.domain.event;

import com.wpanther.cancellationnote.processing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteProcessedDomainEventTest {

    private static final Money TOTAL = Money.of(new BigDecimal("267.50"), "THB");

    @Test
    void constructorWithAllFields() {
        Instant now = Instant.now();
        CancellationNoteProcessedDomainEvent event = new CancellationNoteProcessedDomainEvent(
                "note-123", "CN-001", TOTAL, "INV-001", "saga-456", "corr-789", now
        );

        assertEquals("note-123", event.noteId());
        assertEquals("CN-001", event.cancellationNoteNumber());
        assertEquals(TOTAL, event.total());
        assertEquals("INV-001", event.cancelledInvoiceNumber());
        assertEquals("saga-456", event.sagaId());
        assertEquals("corr-789", event.correlationId());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void factoryMethodOfSetsOccurredAtToNow() {
        Instant before = Instant.now();
        CancellationNoteProcessedDomainEvent event = CancellationNoteProcessedDomainEvent.of(
                "note-123", "CN-001", TOTAL, "INV-001", "saga-456", "corr-789"
        );
        Instant after = Instant.now();

        assertNotNull(event.occurredAt());
        assertFalse(event.occurredAt().isBefore(before));
        assertFalse(event.occurredAt().isAfter(after));
    }

    @Test
    void factoryMethodOfSetsAllFields() {
        CancellationNoteProcessedDomainEvent event = CancellationNoteProcessedDomainEvent.of(
                "note-123", "CN-001", TOTAL, "INV-001", "saga-456", "corr-789"
        );

        assertEquals("note-123", event.noteId());
        assertEquals("CN-001", event.cancellationNoteNumber());
        assertEquals(TOTAL, event.total());
        assertEquals("INV-001", event.cancelledInvoiceNumber());
        assertEquals("saga-456", event.sagaId());
        assertEquals("corr-789", event.correlationId());
    }

    @Test
    void recordEquality() {
        Instant now = Instant.now();
        CancellationNoteProcessedDomainEvent a = new CancellationNoteProcessedDomainEvent(
                "note-123", "CN-001", TOTAL, "INV-001", "saga-456", "corr-789", now
        );
        CancellationNoteProcessedDomainEvent b = new CancellationNoteProcessedDomainEvent(
                "note-123", "CN-001", TOTAL, "INV-001", "saga-456", "corr-789", now
        );
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality() {
        Instant now = Instant.now();
        CancellationNoteProcessedDomainEvent a = new CancellationNoteProcessedDomainEvent(
                "note-123", "CN-001", TOTAL, "INV-001", "saga-456", "corr-789", now
        );
        CancellationNoteProcessedDomainEvent b = new CancellationNoteProcessedDomainEvent(
                "note-999", "CN-002", TOTAL, "INV-002", "saga-999", "corr-999", now
        );
        assertNotEquals(a, b);
    }

    @Test
    void allowsNullNoteIdInRecord() {
        // Record constructor does not enforce null checks; test the raw record behavior
        CancellationNoteProcessedDomainEvent event = new CancellationNoteProcessedDomainEvent(
                null, "CN-001", TOTAL, "INV-001", "saga-456", "corr-789", Instant.now()
        );
        assertNull(event.noteId());
    }
}
