package com.wpanther.cancellationnote.processing.domain.service;

import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;

public interface CancellationNoteParserService {

    ProcessedCancellationNote parseCancellationNote(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException;

    class CancellationNoteParsingException extends Exception {
        public CancellationNoteParsingException(String message) {
            super(message);
        }

        public CancellationNoteParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
