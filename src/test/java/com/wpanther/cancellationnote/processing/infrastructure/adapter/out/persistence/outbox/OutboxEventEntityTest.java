package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventEntityTest {

    @Test
    @DisplayName("fromDomain and toDomain should round-trip correctly")
    void fromDomainAndToDomainShouldRoundTrip() {
        Instant now = Instant.now();
        OutboxEvent original = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("CancellationNote")
                .aggregateId("cn-123")
                .eventType("CancellationNoteProcessed")
                .status(OutboxStatus.PENDING)
                .payload("{\"key\":\"value\"}")
                .topic("cancellationnote.processed")
                .partitionKey("cn-123")
                .headers("{\"header\":\"h1\"}")
                .createdAt(now)
                .publishedAt(null)
                .retryCount(0)
                .errorMessage(null)
                .build();

        OutboxEventEntity entity = OutboxEventEntity.fromDomain(original);
        OutboxEvent restored = entity.toDomain();

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getAggregateType()).isEqualTo("CancellationNote");
        assertThat(restored.getAggregateId()).isEqualTo("cn-123");
        assertThat(restored.getEventType()).isEqualTo("CancellationNoteProcessed");
        assertThat(restored.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(restored.getPayload()).isEqualTo("{\"key\":\"value\"}");
        assertThat(restored.getTopic()).isEqualTo("cancellationnote.processed");
        assertThat(restored.getPartitionKey()).isEqualTo("cn-123");
        assertThat(restored.getHeaders()).isEqualTo("{\"header\":\"h1\"}");
        assertThat(restored.getCreatedAt()).isEqualTo(now);
        assertThat(restored.getRetryCount()).isEqualTo(0);
        assertThat(restored.getPublishedAt()).isNull();
        assertThat(restored.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("builder should create entity with all fields")
    void builderShouldCreateEntity() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("TestAggregate")
                .aggregateId("agg-1")
                .eventType("TestEvent")
                .status(OutboxStatus.PUBLISHED)
                .payload("{\"test\":true}")
                .topic("test.topic")
                .partitionKey("key-1")
                .headers(null)
                .createdAt(now)
                .publishedAt(now)
                .errorMessage(null)
                .updatedAt(now)
                .build();

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getAggregateType()).isEqualTo("TestAggregate");
        assertThat(entity.getAggregateId()).isEqualTo("agg-1");
        assertThat(entity.getEventType()).isEqualTo("TestEvent");
        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(entity.getPayload()).isEqualTo("{\"test\":true}");
        assertThat(entity.getTopic()).isEqualTo("test.topic");
    }
}
