package com.wpanther.cancellationnote.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class CancellationNoteProcessedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "cancellationnote.processed";

    @JsonProperty("noteId")
    private final String noteId;

    @JsonProperty("cancellationNoteNumber")
    private final String cancellationNoteNumber;

    @JsonProperty("total")
    private final BigDecimal total;

    @JsonProperty("currency")
    private final String currency;

    @JsonProperty("cancelledInvoiceNumber")
    private final String cancelledInvoiceNumber;

    @JsonProperty("correlationId")
    private final String correlationId;

    public CancellationNoteProcessedEvent(String noteId, String cancellationNoteNumber, BigDecimal total,
                                     String currency, String cancelledInvoiceNumber, String correlationId) {
        super();
        this.noteId = noteId;
        this.cancellationNoteNumber = cancellationNoteNumber;
        this.total = total;
        this.currency = currency;
        this.cancelledInvoiceNumber = cancelledInvoiceNumber;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public CancellationNoteProcessedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("noteId") String noteId,
            @JsonProperty("cancellationNoteNumber") String cancellationNoteNumber,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("currency") String currency,
            @JsonProperty("cancelledInvoiceNumber") String cancelledInvoiceNumber,
            @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.noteId = noteId;
        this.cancellationNoteNumber = cancellationNoteNumber;
        this.total = total;
        this.currency = currency;
        this.cancelledInvoiceNumber = cancelledInvoiceNumber;
        this.correlationId = correlationId;
    }
}
