package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final Counter cleanupFailureCounter;

    @Value("${app.outbox.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${app.outbox.cleanup.cron:0 0 2 * * *}")
    private String cleanupCron;

    public OutboxCleanupScheduler(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.cleanupFailureCounter = Counter.builder("outbox.cleanup.failure")
            .description("Number of times the outbox cleanup job failed")
            .register(meterRegistry);
    }

    @PostConstruct
    void logConfiguration() {
        if (retentionDays < 1) throw new IllegalStateException("app.outbox.cleanup.retention-days must be >= 1, got: " + retentionDays);
        log.info("OutboxCleanupScheduler initialized: retention={} days, cron='{}'", retentionDays, cleanupCron);
    }

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 2 * * *}")
    public void cleanPublishedEvents() {
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
            log.info("Outbox cleanup: deleted {} published events older than {} days", deleted, retentionDays);
        } catch (Exception e) {
            cleanupFailureCounter.increment();
            log.error("Outbox cleanup failed: {}", e.toString());
        }
    }
}
