package com.wpanther.cancellationnote.processing.domain.model;

import java.util.UUID;

public record CancellationNoteId(UUID value) {
    public static CancellationNoteId generate() {
        return new CancellationNoteId(UUID.randomUUID());
    }

    public CancellationNoteId {
        if (value == null) {
            throw new IllegalArgumentException("CancellationNote ID cannot be null");
        }
    }
}
