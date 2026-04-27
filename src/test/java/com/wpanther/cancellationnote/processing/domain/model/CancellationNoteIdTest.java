package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteIdTest {

    @Test
    void constructorWithValidUuid() {
        UUID uuid = UUID.randomUUID();
        CancellationNoteId id = new CancellationNoteId(uuid);
        assertEquals(uuid, id.value());
    }

    @Test
    void constructorWithNullThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CancellationNoteId(null));
        assertEquals("CancellationNote ID cannot be null", ex.getMessage());
    }

    @Test
    void generateCreatesNonNullId() {
        CancellationNoteId id = CancellationNoteId.generate();
        assertNotNull(id.value());
    }

    @Test
    void generateCreatesUniqueIds() {
        CancellationNoteId id1 = CancellationNoteId.generate();
        CancellationNoteId id2 = CancellationNoteId.generate();
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void recordEquality() {
        UUID uuid = UUID.randomUUID();
        CancellationNoteId id1 = new CancellationNoteId(uuid);
        CancellationNoteId id2 = new CancellationNoteId(uuid);
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void recordInequality() {
        CancellationNoteId id1 = new CancellationNoteId(UUID.randomUUID());
        CancellationNoteId id2 = new CancellationNoteId(UUID.randomUUID());
        assertNotEquals(id1, id2);
    }
}
