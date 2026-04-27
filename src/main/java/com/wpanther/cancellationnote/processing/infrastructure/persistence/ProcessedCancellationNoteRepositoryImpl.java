package com.wpanther.cancellationnote.processing.infrastructure.persistence;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.repository.ProcessedCancellationNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedCancellationNoteRepositoryImpl implements ProcessedCancellationNoteRepository {

    private final JpaProcessedCancellationNoteRepository jpaRepository;

    @Override
    @Transactional
    public ProcessedCancellationNote save(ProcessedCancellationNote note) {
        log.debug("Saving cancellation note: {}", note.cancellationNoteNumber());
        ProcessedCancellationNoteEntity entity = toEntity(note);
        ProcessedCancellationNoteEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findById(CancellationNoteId id) {
        log.debug("Finding cancellation note by id: {}", id.value());
        return jpaRepository.findByIdWithDetails(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findByCancellationNoteNumber(String cancellationNoteNumber) {
        log.debug("Finding cancellation note by number: {}", cancellationNoteNumber);
        return jpaRepository.findByCancellationNoteNumberWithDetails(cancellationNoteNumber).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findBySourceNoteId(String sourceNoteId) {
        log.debug("Finding cancellation note by source ID: {}", sourceNoteId);
        return jpaRepository.findBySourceNoteIdWithDetails(sourceNoteId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(CancellationNoteId id) {
        log.info("Deleting cancellation note: {}", id.value());
        jpaRepository.deleteById(id.value());
    }

    private ProcessedCancellationNoteEntity toEntity(ProcessedCancellationNote domain) {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(domain.id() != null ? domain.id().value() : null)
                .sourceNoteId(domain.sourceNoteId())
                .cancellationNoteNumber(domain.cancellationNoteNumber())
                .issueDate(domain.issueDate())
                .cancellationDate(domain.cancellationDate())
                .cancelledInvoiceNumber(domain.cancelledInvoiceNumber())
                .currency(domain.currency())
                .subtotal(domain.getSubtotal().amount())
                .totalTax(domain.getTotalTax().amount())
                .total(domain.getTotal().amount())
                .originalXml(domain.originalXml())
                .status(domain.status())
                .errorMessage(domain.errorMessage())
                .createdAt(domain.createdAt())
                .completedAt(domain.completedAt())
                .build();

        domain.items().forEach(item -> {
            CancellationNoteLineItemEntity lineItemEntity = new CancellationNoteLineItemEntity(
                    UUID.randomUUID(),
                    entity,
                    domain.items().indexOf(item) + 1,
                    item.description(),
                    item.quantity(),
                    item.unitPrice().amount(),
                    item.unitPrice().currency(),
                    item.taxRate(),
                    item.getLineTotal().amount(),
                    item.getTaxAmount().amount()
            );
            entity.addLineItem(lineItemEntity);
        });

        domain.seller();
        CancellationNotePartyEntity sellerEntity = new CancellationNotePartyEntity(
                UUID.randomUUID(),
                entity,
                CancellationNotePartyEntity.PartyType.SELLER,
                domain.seller().name(),
                domain.seller().taxIdentifier().value(),
                domain.seller().taxIdentifier().scheme(),
                domain.seller().address().streetAddress(),
                domain.seller().address().city(),
                domain.seller().address().postalCode(),
                domain.seller().address().country(),
                domain.seller().email()
        );
        entity.addParty(sellerEntity);

        domain.buyer();
        CancellationNotePartyEntity buyerEntity = new CancellationNotePartyEntity(
                UUID.randomUUID(),
                entity,
                CancellationNotePartyEntity.PartyType.BUYER,
                domain.buyer().name(),
                domain.buyer().taxIdentifier().value(),
                domain.buyer().taxIdentifier().scheme(),
                domain.buyer().address().streetAddress(),
                domain.buyer().address().city(),
                domain.buyer().address().postalCode(),
                domain.buyer().address().country(),
                domain.buyer().email()
        );
        entity.addParty(buyerEntity);

        return entity;
    }

    private ProcessedCancellationNote toDomain(ProcessedCancellationNoteEntity entity) {
        return ProcessedCancellationNote.builder()
                .id(CancellationNoteId.of(entity.getId()))
                .sourceNoteId(entity.getSourceNoteId())
                .cancellationNoteNumber(entity.getCancellationNoteNumber())
                .issueDate(entity.getIssueDate())
                .cancellationDate(entity.getCancellationDate())
                .seller(Party.of(
                        entity.getParties().stream()
                                .filter(p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.SELLER)
                                .findFirst()
                                .map(this::toDomainParty)
                                .orElseThrow(() -> new IllegalStateException("Seller party not found"))
                ))
                .buyer(Party.of(
                        entity.getParties().stream()
                                .filter(p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.BUYER)
                                .findFirst()
                                .map(this::toDomainParty)
                                .orElseThrow(() -> new IllegalStateException("Buyer party not found"))
                ))
                .items(entity.getLineItems().stream()
                                .map(this::toDomainLineItem)
                                .toList())
                .currency(entity.getCurrency())
                .cancelledInvoiceNumber(entity.getCancelledInvoiceNumber())
                .originalXml(entity.getOriginalXml())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private com.wpanther.cancellationnote.processing.domain.model.Party toDomainParty(CancellationNotePartyEntity entity) {
        com.wpanther.cancellationnote.processing.domain.model.TaxIdentifier taxIdentifier =
                com.wpanther.cancellationnote.processing.domain.model.TaxIdentifier.of(
                        entity.getTaxId(), entity.getTaxIdScheme());

        com.wpanther.cancellationnote.processing.domain.model.Address address =
                com.wpanther.cancellationnote.processing.domain.model.Address.of(
                        entity.getStreetAddress(),
                        entity.getCity(),
                        entity.getPostalCode(),
                        entity.getCountry()
                );

        return com.wpanther.cancellationnote.processing.domain.model.Party.of(
                entity.getName(),
                taxIdentifier,
                address,
                entity.getEmail()
        );
    }

    private com.wpanther.cancellationnote.processing.domain.model.LineItem toDomainLineItem(CancellationNoteLineItemEntity entity) {
        com.wpanther.cancellationnote.processing.domain.model.Money unitPrice =
                com.wpanther.cancellationnote.processing.domain.model.Money.of(
                        entity.getUnitPrice(), entity.getCancellationNote().getCurrency());

        return new com.wpanther.cancellationnote.processing.domain.model.LineItem(
                entity.getDescription(),
                entity.getQuantity(),
                unitPrice,
                entity.getTaxRate()
        );
    }
}
