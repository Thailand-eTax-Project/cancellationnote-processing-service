package com.wpanther.cancellationnote.processing.application.service;

import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedEvent;
import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.repository.ProcessedCancellationNoteRepository;
import com.wpanther.cancellationnote.processing.infrastructure.messaging.EventPublisher;
import com.wpanther.cancellationnote.processing.domain.service.CancellationNoteParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancellationNoteProcessingService {

    private final ProcessedCancellationNoteRepository cancellationNoteRepository;
    private final CancellationNoteParserService parserService;
    private final EventPublisher eventPublisher;

    @Transactional
    public void processCancellationNoteForSaga(String documentId, String xmlContent, String correlationId) {
        log.info("Processing cancellation note for source ID: {}", documentId);

        ProcessedCancellationNote cancellationNote = parserService.parseCancellationNote(xmlContent, documentId);

        log.info("Parsed cancellation note {} with {} items",
            cancellationNote.cancellationNoteNumber(),
            cancellationNote.items().size());

        cancellationNoteRepository.save(cancellationNote);

        eventPublisher.publishCancellationNoteProcessed(
            cancellationNote.id().value().toString(),
            cancellationNote.cancellationNoteNumber(),
            cancellationNote.getTotal().amount(),
            cancellationNote.currency(),
            cancellationNote.cancelledInvoiceNumber(),
            correlationId
        );

        log.info("Cancellation note {} processed successfully",
            cancellationNote.cancellationNoteNumber());
    }
}
