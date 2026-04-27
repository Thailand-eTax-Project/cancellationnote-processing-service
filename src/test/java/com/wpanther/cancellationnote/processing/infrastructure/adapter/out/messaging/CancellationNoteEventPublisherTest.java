package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.application.dto.event.CancellationNoteProcessedEvent;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.domain.model.Money;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationNoteEventPublisherTest {
    @Mock private OutboxService outboxService;
    @Mock private HeaderSerializer headerSerializer;
    private CancellationNoteEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CancellationNoteEventPublisher(outboxService, headerSerializer, "cancellationnote.processed");
    }

    private CancellationNoteProcessedDomainEvent makeEvent() {
        return CancellationNoteProcessedDomainEvent.of(
            "note-id-1", "CN-001", Money.of(new BigDecimal("10000.00"), "THB"),
            "INV-001", "saga-1", "corr-1");
    }

    @Test
    void testPublishCallsOutboxWithCorrectTopic() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publish(makeEvent());
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());
        assertEquals("cancellationnote.processed", topicCaptor.getValue());
    }

    @Test
    void testPublishTransformsToKafkaEvent() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publish(makeEvent());
        verify(outboxService).saveWithRouting(
            any(CancellationNoteProcessedEvent.class), eq("ProcessedCancellationNote"),
            eq("note-id-1"), eq("cancellationnote.processed"), eq("note-id-1"), eq("{}"));
    }

    @Test
    void testPublishUsesNoteIdAsAggregateIdAndPartitionKey() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publish(makeEvent());
        ArgumentCaptor<String> aggId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> partKey = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), aggId.capture(), any(), partKey.capture(), any());
        assertEquals("note-id-1", aggId.getValue());
        assertEquals("note-id-1", partKey.getValue());
    }
}
