package com.wpanther.cancellationnote.processing.domain.port.out;

import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort.CancellationNoteParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationNoteParsingExceptionTest {

    @Test
    @DisplayName("forEmpty should create exception with correct message")
    void forEmpty_shouldCreateExceptionWithCorrectMessage() {
        CancellationNoteParsingException ex = CancellationNoteParsingException.forEmpty();

        assertThat(ex.getMessage()).isEqualTo("XML content is null or empty");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("forOversized should include byte sizes in message")
    void forOversized_shouldIncludeByteSizesInMessage() {
        CancellationNoteParsingException ex = CancellationNoteParsingException.forOversized(5000, 1000);

        assertThat(ex.getMessage()).contains("5000 bytes").contains("limit 1000 bytes");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("forTimeout should include timeout value in message")
    void forTimeout_shouldIncludeTimeoutValueInMessage() {
        CancellationNoteParsingException ex = CancellationNoteParsingException.forTimeout(5000);

        assertThat(ex.getMessage()).contains("5000 ms");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("forInterrupted should create exception with correct message")
    void forInterrupted_shouldCreateExceptionWithCorrectMessage() {
        CancellationNoteParsingException ex = CancellationNoteParsingException.forInterrupted();

        assertThat(ex.getMessage()).isEqualTo("XML parsing was interrupted");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("forUnmarshal should wrap the cause")
    void forUnmarshal_shouldWrapTheCause() {
        Throwable cause = new RuntimeException("unmarshal failure");
        CancellationNoteParsingException ex = CancellationNoteParsingException.forUnmarshal(cause);

        assertThat(ex.getMessage()).contains("unmarshal failure");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("forUnexpectedRootElement should include class name in message")
    void forUnexpectedRootElement_shouldIncludeClassNameInMessage() {
        CancellationNoteParsingException ex =
                CancellationNoteParsingException.forUnexpectedRootElement("com.example.SomeType");

        assertThat(ex.getMessage()).contains("com.example.SomeType");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("single-arg constructor should set message without cause")
    void singleArgConstructor_shouldSetMessageWithoutCause() {
        CancellationNoteParsingException ex = new CancellationNoteParsingException("test message");

        assertThat(ex.getMessage()).isEqualTo("test message");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("two-arg constructor should set message and cause")
    void twoArgConstructor_shouldSetMessageAndCause() {
        Throwable cause = new IllegalArgumentException("root cause");
        CancellationNoteParsingException ex =
                new CancellationNoteParsingException("wrapper message", cause);

        assertThat(ex.getMessage()).isEqualTo("wrapper message");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
