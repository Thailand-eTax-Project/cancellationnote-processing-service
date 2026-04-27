package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.ProcessCancellationNoteCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ProcessCancellationNoteUseCase processCancellationNoteUseCase;
    private final CompensateCancellationNoteUseCase compensateCancellationNoteUseCase;

    public void handleProcessCommand(ProcessCancellationNoteCommand command) {
        log.info("Handling ProcessCancellationNoteCommand for saga {} document {}",
            command.getSagaId(), command.getDocumentId());
        try {
            processCancellationNoteUseCase.process(
                command.getDocumentId(), command.getXmlContent(),
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
        } catch (ProcessCancellationNoteUseCase.CancellationNoteProcessingException e) {
            log.error("Failed to process cancellation note for saga {}: {}",
                command.getSagaId(), e.toString(), e);
        }
    }

    public void handleCompensation(CompensateCancellationNoteCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());
        compensateCancellationNoteUseCase.compensate(
            command.getDocumentId(), command.getSagaId(),
            command.getSagaStep(), command.getCorrelationId());
    }
}
