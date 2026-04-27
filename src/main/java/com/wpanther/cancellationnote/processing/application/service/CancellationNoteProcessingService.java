package com.wpanther.cancellationnote.processing.application.service;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.out.CancellationNoteEventPublishingPort;
import com.wpanther.cancellationnote.processing.application.port.out.SagaReplyPort;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.Optional;

@Service
@Slf4j
public class CancellationNoteProcessingService
        implements ProcessCancellationNoteUseCase, CompensateCancellationNoteUseCase {

    private final ProcessedCancellationNoteRepository noteRepository;
    private final CancellationNoteParserPort parserPort;
    private final CancellationNoteEventPublishingPort eventPublisher;
    private final SagaReplyPort sagaReplyPort;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate requiresNewTemplate;

    private final Counter processSuccessCounter;
    private final Counter processFailureCounter;
    private final Counter processIdempotentCounter;
    private final Counter processRaceConditionResolvedCounter;
    private final Counter compensateSuccessCounter;
    private final Counter compensateIdempotentCounter;
    private final Counter compensateFailureCounter;
    private final Timer processingTimer;

    public CancellationNoteProcessingService(
            ProcessedCancellationNoteRepository noteRepository,
            CancellationNoteParserPort parserPort,
            CancellationNoteEventPublishingPort eventPublisher,
            SagaReplyPort sagaReplyPort,
            MeterRegistry meterRegistry,
            PlatformTransactionManager transactionManager) {
        this.noteRepository = noteRepository;
        this.parserPort = parserPort;
        this.eventPublisher = eventPublisher;
        this.sagaReplyPort = sagaReplyPort;
        this.meterRegistry = meterRegistry;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTemplate = template;

        this.processSuccessCounter = Counter.builder("cancellationNote.processing.success")
            .description("Number of successfully processed cancellation notes").register(meterRegistry);
        this.processFailureCounter = Counter.builder("cancellationNote.processing.failure")
            .description("Number of failed cancellation note processing attempts").register(meterRegistry);
        this.processIdempotentCounter = Counter.builder("cancellationNote.processing.idempotent")
            .description("Number of duplicate processing requests handled idempotently").register(meterRegistry);
        this.processRaceConditionResolvedCounter = Counter.builder("cancellationNote.processing.race_condition_resolved")
            .description("Number of DuplicateKeyExceptions on source_note_id resolved as concurrent inserts").register(meterRegistry);
        this.compensateSuccessCounter = Counter.builder("cancellationNote.compensation.success")
            .description("Number of successful compensations").register(meterRegistry);
        this.compensateIdempotentCounter = Counter.builder("cancellationNote.compensation.idempotent")
            .description("Number of duplicate compensation commands for an already-deleted note").register(meterRegistry);
        this.compensateFailureCounter = Counter.builder("cancellationNote.compensation.failure")
            .description("Number of failed compensation attempts").register(meterRegistry);
        this.processingTimer = Timer.builder("cancellationNote.processing.duration")
            .description("Time taken to process cancellation notes").register(meterRegistry);
    }

    @Override
    @Transactional
    public void process(String documentId, String xmlContent, String sagaId,
                        SagaStep sagaStep, String correlationId) throws CancellationNoteProcessingException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            processNoteForSagaInternal(documentId, xmlContent, sagaId, sagaStep, correlationId);
        } catch (CancellationNoteParserPort.CancellationNoteParsingException e) {
            processFailureCounter.increment();
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Parse error: " + e.toString());
            throw new CancellationNoteProcessingException("Failed to parse cancellation note: " + e.toString(), e);
        } catch (DuplicateKeyException e) {
            if (!isSourceNoteIdViolation(e)) {
                processFailureCounter.increment();
                log.error("Duplicate key on non-idempotent constraint for document {}: {}", documentId, e.toString());
                sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                    "Constraint violation for document " + documentId + ": " + e.toString());
                throw new CancellationNoteProcessingException("Constraint violation for document " + documentId, e);
            }
            log.warn("DuplicateKeyException on source_note_id for document {} — re-checking for concurrent insert", documentId);
            requiresNewTemplate.execute(txStatus -> {
                Optional<ProcessedCancellationNote> existing = noteRepository.findBySourceNoteId(documentId);
                if (existing.isPresent()) {
                    log.warn("Race condition resolved: document {} already committed by concurrent thread — replying SUCCESS", documentId);
                    processRaceConditionResolvedCounter.increment();
                    sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                } else {
                    log.error("DuplicateKeyException on source_note_id for document {} but no record found", documentId);
                    processFailureCounter.increment();
                    sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                        "Duplicate key violation for document " + documentId + ": " + e.toString());
                }
                return null;
            });
            throw new CancellationNoteProcessingException("Concurrent insert for document: " + documentId, e);
        } catch (DataIntegrityViolationException e) {
            processFailureCounter.increment();
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Constraint violation for document " + documentId + ": " + e.toString());
            throw new CancellationNoteProcessingException("Constraint violation for document " + documentId, e);
        } catch (Exception e) {
            processFailureCounter.increment();
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Processing error for document " + documentId + ": " + e.toString());
            throw new CancellationNoteProcessingException("Failed to process cancellation note " + documentId, e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processNoteForSagaInternal(String documentId, String xmlContent,
                                             String sagaId, SagaStep sagaStep, String correlationId)
            throws CancellationNoteParserPort.CancellationNoteParsingException {
        Optional<ProcessedCancellationNote> existing = noteRepository.findBySourceNoteId(documentId);
        if (existing.isPresent()) {
            ProcessedCancellationNote existingNote = existing.get();
            if (existingNote.status() == ProcessingStatus.COMPLETED) {
                log.warn("Cancellation note already completed for document {}, returning idempotent success", documentId);
                processIdempotentCounter.increment();
                sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                return;
            }
            if (existingNote.status() == ProcessingStatus.PROCESSING) {
                log.warn("Cancellation note for document {} found in PROCESSING state — resuming completion", documentId);
                existingNote.markCompleted();
                noteRepository.save(existingNote);
                eventPublisher.publish(CancellationNoteProcessedDomainEvent.of(
                    existingNote.sourceNoteId(), existingNote.cancellationNoteNumber(),
                    existingNote.getTotal(), existingNote.cancelledInvoiceNumber(),
                    sagaId, correlationId));
                sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                processSuccessCounter.increment();
                return;
            }
            throw new IllegalStateException("Cancellation note for document " + documentId +
                " has unexpected persisted status: " + existingNote.status());
        }

        ProcessedCancellationNote note = parserPort.parse(xmlContent, documentId);
        note.markProcessing();
        ProcessedCancellationNote saved = noteRepository.save(note);
        saved.markCompleted();
        noteRepository.save(saved);

        eventPublisher.publish(CancellationNoteProcessedDomainEvent.of(
            saved.sourceNoteId(), saved.cancellationNoteNumber(),
            saved.getTotal(), saved.cancelledInvoiceNumber(),
            sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
        processSuccessCounter.increment();
        log.info("Successfully processed cancellation note: {}", saved.cancellationNoteNumber());
    }

    @Override
    @Transactional
    public void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        log.info("Compensating cancellation note for document: {}", documentId);
        try {
            Optional<ProcessedCancellationNote> existing = noteRepository.findBySourceNoteId(documentId);
            if (existing.isPresent()) {
                noteRepository.deleteById(existing.get().id());
                log.info("Deleted cancellation note for document: {}", documentId);
            } else {
                compensateIdempotentCounter.increment();
                log.warn("Cancellation note not found for compensation of document {} — treating as idempotent", documentId);
            }
            sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
            compensateSuccessCounter.increment();
        } catch (Exception e) {
            compensateFailureCounter.increment();
            log.error("Failed to compensate cancellation note for saga {}: {}", sagaId, e.toString(), e);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Compensation failed: " + e.toString());
            throw new CompensateCancellationNoteUseCase.CancellationNoteCompensationException(
                "Compensation failed for document " + documentId, e);
        }
    }

    private static boolean isSourceNoteIdViolation(DuplicateKeyException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause.getMessage();
        if (msg == null || !msg.contains("uq_processed_cancellation_notes_source_note_id")) return false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) return true;
        }
        return false;
    }
}
