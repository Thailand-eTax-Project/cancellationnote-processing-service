package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.parsing;

import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort.CancellationNoteParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteParserServiceImplTest {

    private CancellationNoteParserServiceImpl parserService;

    @BeforeEach
    void setUp() throws CancellationNoteParsingException {
        parserService = new CancellationNoteParserServiceImpl();
    }

    @Test
    void parse_withNullXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(null, "source-1"));
    }

    @Test
    void parse_withBlankXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse("   ", "source-1"));
    }

    @Test
    void parse_withInvalidXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse("<invalid>not-a-cancellation-note</invalid>", "source-1"));
    }

    @Test
    void constructor_initializesJaxbContext_withoutException() {
        assertDoesNotThrow(() -> new CancellationNoteParserServiceImpl());
    }
}
