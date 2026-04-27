package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Getter
public class ProcessCancellationNoteCommand extends SagaCommand {
    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId") private final String documentId;
    @JsonProperty("xmlContent") private final String xmlContent;
    @JsonProperty("cancellationNoteNumber") private final String cancellationNoteNumber;

    @JsonCreator
    public ProcessCancellationNoteCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("cancellationNoteNumber") String cancellationNoteNumber) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.cancellationNoteNumber = cancellationNoteNumber;
    }

    public ProcessCancellationNoteCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                           String documentId, String xmlContent, String cancellationNoteNumber) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.cancellationNoteNumber = cancellationNoteNumber;
    }
}
