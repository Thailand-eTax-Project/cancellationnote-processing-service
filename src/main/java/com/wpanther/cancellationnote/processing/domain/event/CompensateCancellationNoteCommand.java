package com.wpanther.cancellationnote.processing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

public class CompensateCancellationNoteCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("sagaStep")
    private final String sagaStep;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("stepToCompensate")
    private final String stepToCompensate;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    public String sagaId() {
        return sagaId;
    }

    public String sagaStep() {
        return sagaStep;
    }

    public String correlationId() {
        return correlationId;
    }

    public String stepToCompensate() {
        return stepToCompensate;
    }

    public String documentId() {
        return documentId;
    }

    public String documentType() {
        return documentType;
    }

    @JsonCreator
    public CompensateCancellationNoteCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") String sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("stepToCompensate") String stepToCompensate,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version);
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }

    public CompensateCancellationNoteCommand(String sagaId, String sagaStep, String correlationId,
                                        String stepToCompensate, String documentId, String documentType) {
        super();
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }
}
