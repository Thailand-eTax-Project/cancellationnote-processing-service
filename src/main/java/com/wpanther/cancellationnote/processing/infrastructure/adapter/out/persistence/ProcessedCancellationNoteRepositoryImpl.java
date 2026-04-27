package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedCancellationNoteRepositoryImpl implements ProcessedCancellationNoteRepository {

    private final JpaProcessedCancellationNoteRepository jpaRepository;
    private final ProcessedCancellationNoteMapper mapper;

    @Override
    @Transactional
    public ProcessedCancellationNote save(ProcessedCancellationNote note) {
        ProcessedCancellationNoteEntity entity = mapper.toEntity(note);
        ProcessedCancellationNoteEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findById(CancellationNoteId id) {
        return jpaRepository.findByIdWithDetails(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findByCancellationNoteNumber(String cancellationNoteNumber) {
        return jpaRepository.findByCancellationNoteNumberWithDetails(cancellationNoteNumber).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findBySourceNoteId(String sourceNoteId) {
        return jpaRepository.findBySourceNoteIdWithDetails(sourceNoteId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(CancellationNoteId id) {
        jpaRepository.deleteById(id.value());
    }
}
