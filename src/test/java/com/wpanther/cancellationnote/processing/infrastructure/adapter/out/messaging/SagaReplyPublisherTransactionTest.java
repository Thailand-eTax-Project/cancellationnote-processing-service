package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.OutboxEventEntity;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SagaReplyPublisherTransactionTest {

    @Autowired private SagaReplyPublisher sagaReplyPublisher;
    @Autowired private SpringDataOutboxRepository outboxRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() { outboxRepository.deleteAll(); }

    @Test
    void publishFailure_commitsOutboxEntry_evenWhenOuterTransactionIsRollbackOnly() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                status.setRollbackOnly();
                sagaReplyPublisher.publishFailure(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "error");
                return null;
            });
        } catch (UnexpectedRollbackException | TransactionSystemException ignored) {}

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertFalse(entries.isEmpty(),
            "publishFailure() must commit outbox entry even when outer transaction is rolled back");
    }

    @Test
    void publishFailure_outboxEntry_containsFailureStatus() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                status.setRollbackOnly();
                sagaReplyPublisher.publishFailure(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "some error");
                return null;
            });
        } catch (UnexpectedRollbackException | TransactionSystemException ignored) {}

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertFalse(entries.isEmpty());
        assertTrue(entries.get(0).getPayload().contains("FAILURE"));
    }

    @Test
    void publishSuccess_rollsBackOutboxEntry_whenOuterTransactionRollsBack() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                sagaReplyPublisher.publishSuccess(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
                status.setRollbackOnly();
                return null;
            });
        } catch (UnexpectedRollbackException | TransactionSystemException ignored) {}

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertTrue(entries.isEmpty(),
            "publishSuccess() must roll back with the outer transaction");
    }

    @Test
    void publishCompensated_commitsOutboxEntry_togetherWithOuterTransaction() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            sagaReplyPublisher.publishCompensated(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
            return null;
        });

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertFalse(entries.isEmpty());
    }
}
