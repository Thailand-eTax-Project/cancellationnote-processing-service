package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationNoteLineItemEntityTest {

    @Test
    @DisplayName("setters should work correctly")
    void settersShouldWork() {
        CancellationNoteLineItemEntity entity = new CancellationNoteLineItemEntity();
        UUID id = UUID.randomUUID();
        ProcessedCancellationNoteEntity parent = ProcessedCancellationNoteEntity.builder().build();

        entity.setId(id);
        entity.setCancellationNote(parent);
        entity.setLineNumber(1);
        entity.setDescription("Test Item");
        entity.setQuantity(5);
        entity.setUnitPrice(new BigDecimal("100.00"));
        entity.setTaxRate(new BigDecimal("7.00"));
        entity.setLineTotal(new BigDecimal("500.00"));
        entity.setTaxAmount(new BigDecimal("35.00"));

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCancellationNote()).isSameAs(parent);
        assertThat(entity.getLineNumber()).isEqualTo(1);
        assertThat(entity.getDescription()).isEqualTo("Test Item");
        assertThat(entity.getQuantity()).isEqualTo(5);
        assertThat(entity.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(entity.getTaxRate()).isEqualByComparingTo("7.00");
        assertThat(entity.getLineTotal()).isEqualByComparingTo("500.00");
        assertThat(entity.getTaxAmount()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("builder should create entity with all fields")
    void builderShouldCreateEntity() {
        UUID id = UUID.randomUUID();
        ProcessedCancellationNoteEntity parent = ProcessedCancellationNoteEntity.builder().build();

        CancellationNoteLineItemEntity entity = CancellationNoteLineItemEntity.builder()
                .id(id)
                .cancellationNote(parent)
                .lineNumber(2)
                .description("Another Item")
                .quantity(3)
                .unitPrice(new BigDecimal("50.00"))
                .taxRate(new BigDecimal("7.00"))
                .lineTotal(new BigDecimal("150.00"))
                .taxAmount(new BigDecimal("10.50"))
                .build();

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getLineNumber()).isEqualTo(2);
        assertThat(entity.getDescription()).isEqualTo("Another Item");
        assertThat(entity.getQuantity()).isEqualTo(3);
        assertThat(entity.getUnitPrice()).isEqualByComparingTo("50.00");
    }
}
