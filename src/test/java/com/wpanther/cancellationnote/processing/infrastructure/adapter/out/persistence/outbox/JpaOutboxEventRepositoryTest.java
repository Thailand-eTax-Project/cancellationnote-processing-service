package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaOutboxEventRepositoryTest {

    @Mock
    private SpringDataOutboxRepository springRepository;

    private JpaOutboxEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaOutboxEventRepository(springRepository);
    }

    @Test
    @DisplayName("save delegates to spring repository and returns domain object")
    void save_delegatesAndReturnsDomainObject() {
        OutboxEvent event = createOutboxEvent();
        OutboxEventEntity entity = OutboxEventEntity.fromDomain(event);
        OutboxEventEntity savedEntity = OutboxEventEntity.builder()
                .id(event.getId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .status(event.getStatus())
                .payload(event.getPayload())
                .topic(event.getTopic())
                .partitionKey(event.getPartitionKey())
                .headers(event.getHeaders())
                .createdAt(event.getCreatedAt())
                .publishedAt(event.getPublishedAt())
                .errorMessage(event.getErrorMessage())
                .build();

        when(springRepository.save(any(OutboxEventEntity.class))).thenReturn(savedEntity);

        OutboxEvent result = repository.save(event);

        assertThat(result.getId()).isEqualTo(event.getId());
        assertThat(result.getAggregateType()).isEqualTo("CancellationNote");
        verify(springRepository).save(any(OutboxEventEntity.class));
    }

    @Test
    @DisplayName("findById returns empty when not found")
    void findById_returnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(springRepository.findById(id)).thenReturn(Optional.empty());

        Optional<OutboxEvent> result = repository.findById(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById returns domain object when found")
    void findById_returnsDomainWhenFound() {
        UUID id = UUID.randomUUID();
        OutboxEventEntity entity = createEntity(id);
        when(springRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<OutboxEvent> result = repository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("findPendingEvents returns empty list when no pending events")
    void findPendingEvents_returnsEmptyListWhenNone() {
        when(springRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of());

        List<OutboxEvent> result = repository.findPendingEvents(10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findPendingEvents returns mapped domain objects")
    void findPendingEvents_returnsMappedDomainObjects() {
        OutboxEventEntity entity = createEntity(UUID.randomUUID());
        when(springRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(entity));

        List<OutboxEvent> result = repository.findPendingEvents(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateType()).isEqualTo("CancellationNote");
    }

    @Test
    @DisplayName("findFailedEvents returns mapped domain objects")
    void findFailedEvents_returnsMappedDomainObjects() {
        OutboxEventEntity entity = createEntity(UUID.randomUUID());
        entity.setStatus(OutboxStatus.FAILED);
        when(springRepository.findFailedEventsOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(entity));

        List<OutboxEvent> result = repository.findFailedEvents(5);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("deletePublishedBefore delegates to spring repository")
    void deletePublishedBefore_delegatesToSpringRepository() {
        Instant cutoff = Instant.now().minusSeconds(86400);
        when(springRepository.deletePublishedBefore(cutoff)).thenReturn(3);

        int deleted = repository.deletePublishedBefore(cutoff);

        assertThat(deleted).isEqualTo(3);
        verify(springRepository).deletePublishedBefore(cutoff);
    }

    @Test
    @DisplayName("findByAggregate returns mapped domain objects")
    void findByAggregate_returnsMappedDomainObjects() {
        OutboxEventEntity entity = createEntity(UUID.randomUUID());
        when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("CancellationNote", "cn-123"))
                .thenReturn(List.of(entity));

        List<OutboxEvent> result = repository.findByAggregate("CancellationNote", "cn-123");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateId()).isEqualTo("cn-123");
    }

    @Test
    @DisplayName("findByAggregate returns empty list when none found")
    void findByAggregate_returnsEmptyListWhenNone() {
        when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("CancellationNote", "cn-999"))
                .thenReturn(List.of());

        List<OutboxEvent> result = repository.findByAggregate("CancellationNote", "cn-999");

        assertThat(result).isEmpty();
    }

    private OutboxEvent createOutboxEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("CancellationNote")
                .aggregateId("cn-123")
                .eventType("CancellationNoteProcessed")
                .status(OutboxStatus.PENDING)
                .payload("{\"key\":\"value\"}")
                .topic("cancellationnote.processed")
                .partitionKey("cn-123")
                .headers("{\"h\":\"1\"}")
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }

    private OutboxEventEntity createEntity(UUID id) {
        return OutboxEventEntity.builder()
                .id(id)
                .aggregateType("CancellationNote")
                .aggregateId("cn-123")
                .eventType("CancellationNoteProcessed")
                .status(OutboxStatus.PENDING)
                .payload("{\"key\":\"value\"}")
                .topic("cancellationnote.processed")
                .partitionKey("cn-123")
                .headers(null)
                .createdAt(Instant.now())
                .build();
    }
}
