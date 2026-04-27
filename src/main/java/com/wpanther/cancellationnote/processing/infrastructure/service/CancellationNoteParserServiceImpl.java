package com.wpanther.cancellationnote.processing.infrastructure.service;

import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.service.CancellationNoteParserService;
import lombok.extern.slf4j.Slf4j;

// TODO: Delete in Task 11 — replaced by hexagonal adapter at infrastructure/adapter/out/parsing/
@Deprecated
@Slf4j
public class CancellationNoteParserServiceImpl implements CancellationNoteParserService {

    @Override
    public ProcessedCancellationNote parseCancellationNote(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException {
        throw new CancellationNoteParsingException("Not yet implemented - will be rewritten in Task 6");
    }
}
