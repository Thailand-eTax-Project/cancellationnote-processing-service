package com.wpanther.cancellationnote.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface ProcessCancellationNoteUseCase {

    void process(String documentId, String xmlContent,
                 String sagaId, SagaStep sagaStep, String correlationId)
            throws CancellationNoteProcessingException;

    class CancellationNoteProcessingException extends Exception {
        public CancellationNoteProcessingException(String message) {
            super(message);
        }
        public CancellationNoteProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}