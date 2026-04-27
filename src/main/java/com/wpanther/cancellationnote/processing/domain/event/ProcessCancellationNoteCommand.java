package com.wpanther.cancellationnote.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

public class ProcessCancellationNoteCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("sagaStep")
    private final String sagaStep;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("cancellationNoteNumber")
    private final String cancellationNoteNumber;

    public String sagaId() {
        return sagaId;
    }

    public String sagaStep() {
        return sagaStep;
    }

    public String correlationId() {
        return correlationId;
    }

    public String documentId() {
        return documentId;
    }

    public String xmlContent() {
        return xmlContent;
    }

    public String cancellationNoteNumber() {
        return cancellationNoteNumber;
    }

    @JsonCreator
    public ProcessCancellationNoteCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") String sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("cancellationNoteNumber") String cancellationNoteNumber) {
        super(eventId, occurredAt, eventType, version);
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.cancellationNoteNumber = cancellationNoteNumber;
    }

    public ProcessCancellationNoteCommand(String sagaId, String sagaStep, String correlationId,
                                     String documentId, String xmlContent, String cancellationNoteNumber) {
        super();
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.cancellationNoteNumber = cancellationNoteNumber;
    }
}
