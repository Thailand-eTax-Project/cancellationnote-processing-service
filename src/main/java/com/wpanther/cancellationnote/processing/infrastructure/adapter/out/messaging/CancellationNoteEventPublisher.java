package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.application.dto.event.CancellationNoteProcessedEvent;
import com.wpanther.cancellationnote.processing.application.port.out.CancellationNoteEventPublishingPort;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.infrastructure.config.KafkaTopicsProperties;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Component
@Slf4j
public class CancellationNoteEventPublisher implements CancellationNoteEventPublishingPort {

    private static final String AGGREGATE_TYPE = "ProcessedCancellationNote";

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;
    private final String cancellationNoteProcessedTopic;

    @Autowired
    public CancellationNoteEventPublisher(OutboxService outboxService, HeaderSerializer headerSerializer,
                                           KafkaTopicsProperties topics) {
        this(outboxService, headerSerializer, topics.cancellationNoteProcessed());
    }

    CancellationNoteEventPublisher(OutboxService outboxService, HeaderSerializer headerSerializer,
                                    String cancellationNoteProcessedTopic) {
        this.outboxService = outboxService;
        this.headerSerializer = headerSerializer;
        this.cancellationNoteProcessedTopic = cancellationNoteProcessedTopic;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(CancellationNoteProcessedDomainEvent event) {
        CancellationNoteProcessedEvent kafkaEvent = new CancellationNoteProcessedEvent(
            event.noteId(), event.cancellationNoteNumber(),
            event.total().amount(), event.total().currency(),
            event.cancelledInvoiceNumber(), event.correlationId());

        outboxService.saveWithRouting(
            kafkaEvent, AGGREGATE_TYPE, event.noteId(),
            cancellationNoteProcessedTopic, event.noteId(),
            headerSerializer.toJson(Map.of(
                "correlationId", event.correlationId(),
                "cancellationNoteNumber", event.cancellationNoteNumber())));

        log.info("Published CancellationNoteProcessedEvent to outbox: {}", event.cancellationNoteNumber());
    }
}
