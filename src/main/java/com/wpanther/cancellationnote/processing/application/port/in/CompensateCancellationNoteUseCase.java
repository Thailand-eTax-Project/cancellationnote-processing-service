package com.wpanther.cancellationnote.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface CompensateCancellationNoteUseCase {

    void compensate(String documentId, String sagaId,
                    SagaStep sagaStep, String correlationId);

    class CancellationNoteCompensationException extends RuntimeException {
        public CancellationNoteCompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}