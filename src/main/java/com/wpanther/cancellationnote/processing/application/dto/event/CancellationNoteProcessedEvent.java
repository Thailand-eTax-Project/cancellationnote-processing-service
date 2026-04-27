package com.wpanther.cancellationnote.processing.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class CancellationNoteProcessedEvent extends TraceEvent {

    private static final String EVENT_TYPE = "cancellationnote.processed";
    private static final String SOURCE = "cancellationnote-processing-service";
    private static final String TRACE_TYPE = "CANCELLATION_NOTE_PROCESSED";

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

    public CancellationNoteProcessedEvent(String noteId, String cancellationNoteNumber,
                                          BigDecimal total, String currency,
                                          String cancelledInvoiceNumber, String correlationId) {
        super(null, correlationId, SOURCE, TRACE_TYPE, null);
        this.noteId = noteId;
        this.cancellationNoteNumber = cancellationNoteNumber;
        this.total = total;
        this.currency = currency;
        this.cancelledInvoiceNumber = cancelledInvoiceNumber;
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
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("source") String source,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("noteId") String noteId,
            @JsonProperty("cancellationNoteNumber") String cancellationNoteNumber,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("currency") String currency,
            @JsonProperty("cancelledInvoiceNumber") String cancelledInvoiceNumber) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.noteId = noteId;
        this.cancellationNoteNumber = cancellationNoteNumber;
        this.total = total;
        this.currency = currency;
        this.cancelledInvoiceNumber = cancelledInvoiceNumber;
    }
}