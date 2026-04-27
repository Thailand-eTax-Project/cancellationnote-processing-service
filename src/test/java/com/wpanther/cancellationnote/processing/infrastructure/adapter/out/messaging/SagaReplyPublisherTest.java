package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging.dto.CancellationNoteReplyEvent;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaReplyPublisherTest {
    @Mock private OutboxService outboxService;
    @Mock private HeaderSerializer headerSerializer;
    private SagaReplyPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SagaReplyPublisher(outboxService, headerSerializer, "saga.reply.cancellation-note");
    }

    @Test
    void testPublishSuccessCallsOutboxWithCorrectParameters() {
        when(headerSerializer.toJson(any())).thenReturn("{\"status\":\"SUCCESS\"}");
        publisher.publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        verify(outboxService).saveWithRouting(
            any(CancellationNoteReplyEvent.class), eq("ProcessedCancellationNote"),
            eq("saga-1"), eq("saga.reply.cancellation-note"), eq("saga-1"), contains("SUCCESS"));
    }

    @Test
    void testPublishFailureCallsOutboxWithCorrectParameters() {
        when(headerSerializer.toJson(any())).thenReturn("{\"status\":\"FAILURE\"}");
        publisher.publishFailure("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "Parse error");
        verify(outboxService).saveWithRouting(
            any(CancellationNoteReplyEvent.class), eq("ProcessedCancellationNote"),
            eq("saga-1"), eq("saga.reply.cancellation-note"), eq("saga-1"), contains("FAILURE"));
    }

    @Test
    void testPublishCompensatedCallsOutboxWithCorrectParameters() {
        when(headerSerializer.toJson(any())).thenReturn("{\"status\":\"COMPENSATED\"}");
        publisher.publishCompensated("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        verify(outboxService).saveWithRouting(
            any(CancellationNoteReplyEvent.class), eq("ProcessedCancellationNote"),
            eq("saga-1"), eq("saga.reply.cancellation-note"), eq("saga-1"), contains("COMPENSATED"));
    }

    @Test
    void testPublishSuccessUsesSagaIdAsPartitionKey() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publishSuccess("my-saga-id", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), captor.capture(), any());
        assertEquals("my-saga-id", captor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectAggregateType() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publishFailure("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "error");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), captor.capture(), any(), any(), any(), any());
        assertEquals("ProcessedCancellationNote", captor.getValue());
    }
}
