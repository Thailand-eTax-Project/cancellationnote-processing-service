package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private Counter cleanupFailureCounter;

    private OutboxCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxCleanupScheduler(outboxEventRepository, mock(MeterRegistry.class));
        ReflectionTestUtils.setField(scheduler, "cleanupFailureCounter", cleanupFailureCounter);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "cleanupCron", "0 0 2 * * *");
    }

    @Test
    @DisplayName("should delete published events older than retention period")
    void shouldDeletePublishedEvents() {
        when(outboxEventRepository.deletePublishedBefore(any(Instant.class))).thenReturn(5);

        scheduler.cleanPublishedEvents();

        verify(outboxEventRepository).deletePublishedBefore(any(Instant.class));
    }

    @Test
    @DisplayName("should not throw exception when repository fails")
    void shouldNotThrowOnRepositoryFailure() {
        when(outboxEventRepository.deletePublishedBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("Database error"));

        scheduler.cleanPublishedEvents();

        verify(outboxEventRepository).deletePublishedBefore(any(Instant.class));
        verify(cleanupFailureCounter).increment();
    }

    @Test
    @DisplayName("logConfiguration should throw on invalid retention days")
    void logConfigurationShouldThrowOnInvalidRetention() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scheduler.logConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be >= 1");
    }
}
