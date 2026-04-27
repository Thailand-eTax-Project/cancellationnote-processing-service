package com.wpanther.cancellationnote.processing.domain.port.out;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;

import java.util.Optional;

public interface ProcessedCancellationNoteRepository {

    ProcessedCancellationNote save(ProcessedCancellationNote note);

    Optional<ProcessedCancellationNote> findById(CancellationNoteId id);

    Optional<ProcessedCancellationNote> findByCancellationNoteNumber(String cancellationNoteNumber);

    Optional<ProcessedCancellationNote> findBySourceNoteId(String sourceNoteId);

    void deleteById(CancellationNoteId id);
}