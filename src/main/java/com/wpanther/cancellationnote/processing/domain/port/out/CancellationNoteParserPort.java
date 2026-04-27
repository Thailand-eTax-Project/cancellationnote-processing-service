package com.wpanther.cancellationnote.processing.domain.port.out;

import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;

public interface CancellationNoteParserPort {

    ProcessedCancellationNote parse(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException;

    class CancellationNoteParsingException extends Exception {
        public CancellationNoteParsingException(String message) {
            super(message);
        }

        public CancellationNoteParsingException(String message, Throwable cause) {
            super(message, cause);
        }

        public static CancellationNoteParsingException forEmpty() {
            return new CancellationNoteParsingException("XML content is null or empty");
        }

        public static CancellationNoteParsingException forOversized(int byteSize, int limitBytes) {
            return new CancellationNoteParsingException(
                "XML payload too large: " + byteSize + " bytes (limit " + limitBytes + " bytes)");
        }

        public static CancellationNoteParsingException forTimeout(long timeoutMs) {
            return new CancellationNoteParsingException(
                "XML parsing timed out after " + timeoutMs + " ms — possible malformed input");
        }

        public static CancellationNoteParsingException forInterrupted() {
            return new CancellationNoteParsingException("XML parsing was interrupted");
        }

        public static CancellationNoteParsingException forUnmarshal(Throwable cause) {
            return new CancellationNoteParsingException("XML parsing failed: " + cause.getMessage(), cause);
        }

        public static CancellationNoteParsingException forUnexpectedRootElement(String className) {
            return new CancellationNoteParsingException("Unexpected root element: " + className);
        }
    }
}