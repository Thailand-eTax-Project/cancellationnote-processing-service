package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedCancellationNoteEntityTest {

    @Test
    @DisplayName("builder should create entity with all fields")
    void builderShouldCreateEntity() {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(UUID.randomUUID())
                .sourceNoteId("TIV0100000001001")
                .cancellationNoteNumber("CN001")
                .issueDate(LocalDate.of(2024, 1, 15))
                .cancellationDate(LocalDate.of(2024, 2, 1))
                .cancelledInvoiceNumber("INV001")
                .currency("THB")
                .subtotal(new BigDecimal("400.00"))
                .totalTax(new BigDecimal("28.00"))
                .total(new BigDecimal("428.00"))
                .originalXml("<xml>test</xml>")
                .status(ProcessingStatus.COMPLETED)
                .errorMessage(null)
                .build();

        assertThat(entity.getSourceNoteId()).isEqualTo("TIV0100000001001");
        assertThat(entity.getCancellationNoteNumber()).isEqualTo("CN001");
        assertThat(entity.getIssueDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(entity.getCancellationDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(entity.getCurrency()).isEqualTo("THB");
        assertThat(entity.getSubtotal()).isEqualByComparingTo("400.00");
        assertThat(entity.getTotalTax()).isEqualByComparingTo("28.00");
        assertThat(entity.getTotal()).isEqualByComparingTo("428.00");
        assertThat(entity.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(entity.getParties()).isNotNull().isEmpty();
        assertThat(entity.getLineItems()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("addParty should set back-reference")
    void addPartyShouldSetBackReference() {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(UUID.randomUUID())
                .sourceNoteId("TIV0100000001001")
                .cancellationNoteNumber("CN001")
                .issueDate(LocalDate.of(2024, 1, 15))
                .cancellationDate(LocalDate.of(2024, 2, 1))
                .cancelledInvoiceNumber("INV001")
                .currency("THB")
                .subtotal(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .originalXml("<xml/>")
                .status(ProcessingStatus.PENDING)
                .parties(new HashSet<>())
                .lineItems(new java.util.ArrayList<>())
                .build();

        CancellationNotePartyEntity party = new CancellationNotePartyEntity();
        party.setId(UUID.randomUUID());
        party.setPartyType(CancellationNotePartyEntity.PartyType.SELLER);
        party.setName("Seller Co");
        party.setCountry("TH");

        entity.addParty(party);

        assertThat(entity.getParties()).contains(party);
        assertThat(party.getCancellationNote()).isSameAs(entity);
    }

    @Test
    @DisplayName("addLineItem should set back-reference")
    void addLineItemShouldSetBackReference() {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(UUID.randomUUID())
                .sourceNoteId("TIV0100000001001")
                .cancellationNoteNumber("CN001")
                .issueDate(LocalDate.of(2024, 1, 15))
                .cancellationDate(LocalDate.of(2024, 2, 1))
                .cancelledInvoiceNumber("INV001")
                .currency("THB")
                .subtotal(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .originalXml("<xml/>")
                .status(ProcessingStatus.PENDING)
                .parties(new HashSet<>())
                .lineItems(new java.util.ArrayList<>())
                .build();

        CancellationNoteLineItemEntity lineItem = new CancellationNoteLineItemEntity();
        lineItem.setId(UUID.randomUUID());
        lineItem.setLineNumber(1);
        lineItem.setDescription("Test Item");
        lineItem.setQuantity(1);
        lineItem.setUnitPrice(new BigDecimal("100.00"));
        lineItem.setTaxRate(new BigDecimal("7.00"));
        lineItem.setLineTotal(new BigDecimal("100.00"));
        lineItem.setTaxAmount(new BigDecimal("7.00"));

        entity.addLineItem(lineItem);

        assertThat(entity.getLineItems()).contains(lineItem);
        assertThat(lineItem.getCancellationNote()).isSameAs(entity);
    }
}
