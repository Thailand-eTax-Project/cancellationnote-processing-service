package com.wpanther.cancellationnote.processing.domain.event;

import com.wpanther.cancellationnote.processing.domain.model.Money;

import java.time.Instant;

public record CancellationNoteProcessedDomainEvent(
    String noteId,
    String cancellationNoteNumber,
    Money total,
    String cancelledInvoiceNumber,
    String sagaId,
    String correlationId,
    Instant occurredAt
) {
    public static CancellationNoteProcessedDomainEvent of(
            String noteId,
            String cancellationNoteNumber,
            Money total,
            String cancelledInvoiceNumber,
            String sagaId,
            String correlationId) {
        return new CancellationNoteProcessedDomainEvent(
            noteId, cancellationNoteNumber, total, cancelledInvoiceNumber,
            sagaId, correlationId, Instant.now());
    }
}