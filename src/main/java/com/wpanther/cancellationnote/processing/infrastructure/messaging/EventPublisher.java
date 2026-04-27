package com.wpanther.cancellationnote.processing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private static final String CANCELLATIONNOTE_PROCESSED_TOPIC = "cancellationnote.processed";
    private static final String AGGREGATE_TYPE = "ProcessedCancellationNote";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCancellationNoteProcessed(String noteId, String cancellationNoteNumber,
                                               BigDecimal total, String currency,
                                               String cancelledInvoiceNumber, String correlationId) {
        CancellationNoteProcessedEvent event = new CancellationNoteProcessedEvent(
            noteId,
            cancellationNoteNumber,
            total,
            currency,
            cancelledInvoiceNumber,
            correlationId
        );

        Map<String, String> headers = Map.of(
            "noteId", noteId,
            "cancellationNoteNumber", cancellationNoteNumber,
            "correlationId", correlationId
        );

        outboxService.saveWithRouting(
            event,
            AGGREGATE_TYPE,
            noteId,
            CANCELLATIONNOTE_PROCESSED_TOPIC,
            noteId,
            toJson(headers)
        );

        log.info("Published CancellationNoteProcessedEvent for note {}", cancellationNoteNumber);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers to JSON", e);
            return null;
        }
    }
}
