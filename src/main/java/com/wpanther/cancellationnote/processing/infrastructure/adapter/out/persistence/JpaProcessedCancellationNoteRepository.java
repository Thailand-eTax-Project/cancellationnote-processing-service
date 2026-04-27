package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface JpaProcessedCancellationNoteRepository extends JpaRepository<ProcessedCancellationNoteEntity, UUID> {

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n LEFT JOIN FETCH n.parties LEFT JOIN FETCH n.lineItems WHERE n.id = :id")
    Optional<ProcessedCancellationNoteEntity> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n LEFT JOIN FETCH n.parties LEFT JOIN FETCH n.lineItems WHERE n.cancellationNoteNumber = :number")
    Optional<ProcessedCancellationNoteEntity> findByCancellationNoteNumberWithDetails(@Param("number") String cancellationNoteNumber);

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n LEFT JOIN FETCH n.parties LEFT JOIN FETCH n.lineItems WHERE n.sourceNoteId = :sourceNoteId")
    Optional<ProcessedCancellationNoteEntity> findBySourceNoteIdWithDetails(@Param("sourceNoteId") String sourceNoteId);
}
