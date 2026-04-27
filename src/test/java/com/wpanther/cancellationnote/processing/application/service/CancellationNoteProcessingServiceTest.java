package com.wpanther.cancellationnote.processing.application.service;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.out.CancellationNoteEventPublishingPort;
import com.wpanther.cancellationnote.processing.application.port.out.SagaReplyPort;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationNoteProcessingServiceTest {

    @Mock private ProcessedCancellationNoteRepository noteRepository;
    @Mock private CancellationNoteParserPort parserPort;
    @Mock private CancellationNoteEventPublishingPort eventPublisher;
    @Mock private SagaReplyPort sagaReplyPort;
    @Mock private PlatformTransactionManager transactionManager;

    private CancellationNoteProcessingService service;
    private ProcessedCancellationNote validNote;

    @BeforeEach
    void setUp() {
        service = new CancellationNoteProcessingService(
            noteRepository, parserPort, eventPublisher, sagaReplyPort,
            new SimpleMeterRegistry(), transactionManager);

        Party seller = Party.of("Seller Co", TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 St", "Bangkok", "10110", "TH"), null);
        Party buyer = Party.of("Buyer Co", TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Rd", "Chiang Mai", "50000", "TH"), null);
        LineItem item = new LineItem("Service", 1, Money.of(new BigDecimal("1000.00"), "THB"), new BigDecimal("7.00"));

        validNote = ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate())
            .sourceNoteId("intake-123")
            .cancellationNoteNumber("CN-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .cancellationDate(LocalDate.of(2025, 1, 1))
            .seller(seller).buyer(buyer).items(List.of(item))
            .currency("THB").cancelledInvoiceNumber("INV-001")
            .originalXml("<xml/>")
            .build();
    }

    @Test
    void process_success_savesAndPublishes() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any())).thenReturn(validNote);

        service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(noteRepository).findBySourceNoteId("intake-123");
        verify(parserPort).parse("<xml/>", "intake-123");
        verify(noteRepository, times(2)).save(any());
        verify(eventPublisher).publish(any(CancellationNoteProcessedDomainEvent.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void process_idempotent_completed_publishesSuccessWithoutReprocessing() throws Exception {
        ProcessedCancellationNote completed = ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate()).sourceNoteId("intake-123")
            .cancellationNoteNumber("CN-001").issueDate(LocalDate.of(2025, 1, 1))
            .cancellationDate(LocalDate.of(2025, 1, 1))
            .seller(validNote.seller()).buyer(validNote.buyer()).items(validNote.items())
            .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
            .status(ProcessingStatus.COMPLETED).build();
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.of(completed));

        service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(parserPort, never()).parse(anyString(), anyString());
        verify(noteRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void process_resumesFromProcessingState() throws Exception {
        ProcessedCancellationNote processing = ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate()).sourceNoteId("intake-123")
            .cancellationNoteNumber("CN-001").issueDate(LocalDate.of(2025, 1, 1))
            .cancellationDate(LocalDate.of(2025, 1, 1))
            .seller(validNote.seller()).buyer(validNote.buyer()).items(validNote.items())
            .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
            .status(ProcessingStatus.PROCESSING).build();
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.of(processing));

        service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(parserPort, never()).parse(anyString(), anyString());
        verify(noteRepository, times(1)).save(any());
        verify(eventPublisher).publish(any(CancellationNoteProcessedDomainEvent.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        assertEquals(ProcessingStatus.COMPLETED, processing.status());
    }

    @Test
    void process_parsingError_publishesFailureAndThrows() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString()))
            .thenThrow(new CancellationNoteParserPort.CancellationNoteParsingException("Parse error"));

        assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
            () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Parse error"));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void process_databaseError_publishesFailureAndThrows() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
            () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Processing error"));
    }

    @Test
    void process_raceConditionResolvesAsSuccess_whenRecordFoundOnRecheck() throws Exception {
        SQLException sqlCause = new SQLException(
            "ERROR: duplicate key value violates unique constraint " +
            "\"uq_processed_cancellation_notes_source_note_id\"", "23505");
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(noteRepository.findBySourceNoteId(anyString()))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(validNote));
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate key", sqlCause));

        ProcessCancellationNoteUseCase.CancellationNoteProcessingException ex =
            assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
                () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());

        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        verify(sagaReplyPort, never()).publishFailure(any(), any(), any(), any());
    }

    @Test
    void process_duplicateKeyOnNonIdempotentConstraint_publishesFailure() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any()))
            .thenThrow(new DuplicateKeyException("duplicate key violation \"idx_cn_number_unique\""));

        assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
            () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Constraint violation"));
        verify(noteRepository, times(1)).findBySourceNoteId(anyString());
    }

    @Test
    void compensate_deletesAndPublishesCompensated() throws Exception {
        when(noteRepository.findBySourceNoteId("intake-123")).thenReturn(Optional.of(validNote));

        service.compensate("intake-123", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(noteRepository).deleteById(validNote.id());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void compensate_notFound_publishesCompensatedIdempotently() throws Exception {
        when(noteRepository.findBySourceNoteId("intake-missing")).thenReturn(Optional.empty());

        service.compensate("intake-missing", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(noteRepository, never()).deleteById(any());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void compensate_deleteThrows_publishesFailureAndRethrows() {
        when(noteRepository.findBySourceNoteId("intake-123")).thenReturn(Optional.of(validNote));
        doThrow(new RuntimeException("DB error")).when(noteRepository).deleteById(any());

        assertThrows(CompensateCancellationNoteUseCase.CancellationNoteCompensationException.class,
            () -> service.compensate("intake-123", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Compensation failed"));
    }
}
