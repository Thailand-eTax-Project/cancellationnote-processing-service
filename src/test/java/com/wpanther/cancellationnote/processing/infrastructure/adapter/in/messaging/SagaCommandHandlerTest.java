package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.ProcessCancellationNoteCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCommandHandlerTest {

    @Mock private ProcessCancellationNoteUseCase processUseCase;
    @Mock private CompensateCancellationNoteUseCase compensateUseCase;
    @InjectMocks private SagaCommandHandler handler;

    @Test
    void handleProcessCommand_delegatesToUseCase() throws Exception {
        ProcessCancellationNoteCommand cmd = new ProcessCancellationNoteCommand(
            "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "doc-1", "<xml/>", "CN-001");

        handler.handleProcessCommand(cmd);

        verify(processUseCase).process("doc-1", "<xml/>", "saga-1",
            SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void handleProcessCommand_swallowsProcessingException() throws Exception {
        ProcessCancellationNoteCommand cmd = new ProcessCancellationNoteCommand(
            "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "doc-1", "<xml/>", "CN-001");
        doThrow(new ProcessCancellationNoteUseCase.CancellationNoteProcessingException("err"))
            .when(processUseCase).process(anyString(), anyString(), anyString(), any(), anyString());

        handler.handleProcessCommand(cmd); // must not throw
    }

    @Test
    void handleCompensation_delegatesToUseCase() {
        CompensateCancellationNoteCommand cmd = new CompensateCancellationNoteCommand(
            "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "PROCESS_CANCELLATION_NOTE", "doc-1", "CANCELLATION_NOTE");

        handler.handleCompensation(cmd);

        verify(compensateUseCase).compensate("doc-1", "saga-1",
            SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }
}
