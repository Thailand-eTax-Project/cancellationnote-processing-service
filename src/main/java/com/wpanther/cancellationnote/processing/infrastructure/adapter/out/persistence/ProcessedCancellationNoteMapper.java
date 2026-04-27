package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.CancellationNotePartyEntity.PartyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ProcessedCancellationNoteMapper {

    public ProcessedCancellationNoteEntity toEntity(ProcessedCancellationNote domain) {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(domain.id() != null ? domain.id().value() : UUID.randomUUID())
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

        entity.addParty(toPartyEntity(domain.seller(), PartyType.SELLER, entity));
        entity.addParty(toPartyEntity(domain.buyer(), PartyType.BUYER, entity));

        int lineNumber = 1;
        for (LineItem item : domain.items()) {
            entity.addLineItem(toLineItemEntity(item, lineNumber++, entity));
        }
        return entity;
    }

    public ProcessedCancellationNote toDomain(ProcessedCancellationNoteEntity entity) {
        Party seller = null;
        Party buyer = null;

        for (CancellationNotePartyEntity partyEntity : entity.getParties()) {
            if (partyEntity == null) continue;
            Party party = toPartyDomain(partyEntity);
            if (partyEntity.getPartyType() == PartyType.SELLER) seller = party;
            else if (partyEntity.getPartyType() == PartyType.BUYER) buyer = party;
        }

        if (seller == null) throw new IllegalStateException("No SELLER party found for cancellation note " + entity.getId());
        if (buyer == null) throw new IllegalStateException("No BUYER party found for cancellation note " + entity.getId());

        List<LineItem> items = new ArrayList<>();
        for (CancellationNoteLineItemEntity itemEntity : entity.getLineItems()) {
            if (itemEntity != null) items.add(toLineItemDomain(itemEntity, entity.getCurrency()));
        }

        return ProcessedCancellationNote.builder()
                .id(new CancellationNoteId(entity.getId()))
                .sourceNoteId(entity.getSourceNoteId())
                .cancellationNoteNumber(entity.getCancellationNoteNumber())
                .issueDate(entity.getIssueDate())
                .cancellationDate(entity.getCancellationDate())
                .seller(seller)
                .buyer(buyer)
                .items(items)
                .currency(entity.getCurrency())
                .cancelledInvoiceNumber(entity.getCancelledInvoiceNumber())
                .originalXml(entity.getOriginalXml())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private CancellationNotePartyEntity toPartyEntity(Party domain, PartyType partyType, ProcessedCancellationNoteEntity parent) {
        CancellationNotePartyEntity entity = new CancellationNotePartyEntity();
        entity.setId(UUID.randomUUID());
        entity.setCancellationNote(parent);
        entity.setPartyType(partyType);
        entity.setName(domain.name());
        entity.setTaxId(domain.taxIdentifier() != null ? domain.taxIdentifier().value() : null);
        entity.setTaxIdScheme(domain.taxIdentifier() != null ? domain.taxIdentifier().scheme() : null);
        entity.setStreetAddress(domain.address() != null ? domain.address().streetAddress() : null);
        entity.setCity(domain.address() != null ? domain.address().city() : null);
        entity.setPostalCode(domain.address() != null ? domain.address().postalCode() : null);
        entity.setCountry(domain.address() != null ? domain.address().country() : null);
        entity.setEmail(domain.email());
        return entity;
    }

    private Party toPartyDomain(CancellationNotePartyEntity entity) {
        TaxIdentifier taxId = entity.getTaxId() != null ? TaxIdentifier.of(entity.getTaxId(), entity.getTaxIdScheme()) : null;
        Address address = entity.getCountry() != null ? Address.of(entity.getStreetAddress(), entity.getCity(), entity.getPostalCode(), entity.getCountry()) : null;
        return Party.of(entity.getName(), taxId, address, entity.getEmail());
    }

    private CancellationNoteLineItemEntity toLineItemEntity(LineItem domain, int lineNumber, ProcessedCancellationNoteEntity parent) {
        CancellationNoteLineItemEntity entity = new CancellationNoteLineItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setCancellationNote(parent);
        entity.setLineNumber(lineNumber);
        entity.setDescription(domain.description());
        entity.setQuantity(domain.quantity());
        entity.setUnitPrice(domain.unitPrice().amount());
        entity.setTaxRate(domain.taxRate());
        entity.setLineTotal(domain.getLineTotal().amount());
        entity.setTaxAmount(domain.getTaxAmount().amount());
        return entity;
    }

    private LineItem toLineItemDomain(CancellationNoteLineItemEntity entity, String currency) {
        return new LineItem(entity.getDescription(), entity.getQuantity(), Money.of(entity.getUnitPrice(), currency), entity.getTaxRate());
    }
}
