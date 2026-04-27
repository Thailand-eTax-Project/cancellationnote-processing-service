package com.wpanther.cancellationnote.processing.application.service;

import com.wpanther.cancellationnote.processing.domain.event.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.domain.event.ProcessCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.repository.ProcessedCancellationNoteRepository;
import com.wpanther.cancellationnote.processing.infrastructure.messaging.SagaReplyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final CancellationNoteProcessingService processingService;
    private final SagaReplyPublisher sagaReplyPublisher;
    private final ProcessedCancellationNoteRepository cancellationNoteRepository;

    @Transactional
    public void handleProcessCommand(ProcessCancellationNoteCommand command) {
        log.info("Handling ProcessCancellationNoteCommand for saga {} document {}",
            command.sagaId(), command.documentId());

        try {
            processingService.processCancellationNoteForSaga(
                command.documentId(),
                command.xmlContent(),
                command.correlationId()
            );

            sagaReplyPublisher.publishSuccess(
                command.sagaId(),
                command.sagaStep(),
                command.correlationId()
            );

            log.info("Successfully processed cancellation note for saga {}", command.sagaId());

        } catch (Exception e) {
            log.error("Failed to process cancellation note for saga {}: {}",
                command.sagaId(), e.getMessage(), e);

            sagaReplyPublisher.publishFailure(
                command.sagaId(),
                command.sagaStep(),
                command.correlationId(),
                e.getMessage()
            );
        }
    }

    @Transactional
    public void handleCompensation(CompensateCancellationNoteCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.sagaId(), command.documentId());

        try {
            Optional<ProcessedCancellationNote> existing =
                cancellationNoteRepository.findBySourceNoteId(command.documentId());

            if (existing.isPresent()) {
                cancellationNoteRepository.deleteById(existing.get().id());
                log.info("Deleted ProcessedCancellationNote {} for compensation",
                    existing.get().id());
            } else {
                log.info("No ProcessedCancellationNote found for document {} - already compensated or never processed",
                    command.documentId());
            }

            sagaReplyPublisher.publishCompensated(
                command.sagaId(),
                command.sagaStep(),
                command.correlationId()
            );

        } catch (Exception e) {
            log.error("Failed to compensate cancellation note for saga {}: {}",
                command.sagaId(), e.getMessage(), e);

            sagaReplyPublisher.publishFailure(
                command.sagaId(),
                command.sagaStep(),
                command.correlationId(),
                "Compensation failed: " + e.getMessage()
            );
        }
    }
}
