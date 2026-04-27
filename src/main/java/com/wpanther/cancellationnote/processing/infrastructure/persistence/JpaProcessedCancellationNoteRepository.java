package com.wpanther.cancellationnote.processing.infrastructure.persistence;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaProcessedCancellationNoteRepository extends JpaRepository<ProcessedCancellationNoteEntity, UUID> {

    @Query("SELECT DISTINCT n FROM ProcessedCancellationNoteEntity n " +
            "LEFT JOIN FETCH n.parties p " +
            "LEFT JOIN FETCH n.lineItems li " +
            "WHERE n.id = :id")
    ProcessedCancellationNoteEntity findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT DISTINCT n FROM ProcessedCancellationNoteEntity n " +
            "LEFT JOIN FETCH n.parties p " +
            "LEFT JOIN FETCH n.lineItems li " +
            "WHERE n.status = :status " +
            "ORDER BY n.createdAt DESC")
    java.util.List<ProcessedCancellationNoteEntity> findByStatusWithDetails(@Param("status") com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus status);

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n " +
            "LEFT JOIN FETCH n.parties p " +
            "LEFT JOIN FETCH n.lineItems li " +
            "WHERE n.cancellationNoteNumber = :number")
    Optional<ProcessedCancellationNoteEntity> findByCancellationNoteNumberWithDetails(@Param("number") String number);

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n " +
            "LEFT JOIN FETCH n.parties p " +
            "LEFT JOIN FETCH n.lineItems li " +
            "WHERE n.sourceNoteId = :sourceNoteId")
    Optional<ProcessedCancellationNoteEntity> findBySourceNoteIdWithDetails(@Param("sourceNoteId") String sourceNoteId);
}
