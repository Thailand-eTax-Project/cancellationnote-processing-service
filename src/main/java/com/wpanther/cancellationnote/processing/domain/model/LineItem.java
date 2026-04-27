package com.wpanther.cancellationnote.processing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record LineItem(String description, int quantity, Money unitPrice, BigDecimal taxRate) {

    public LineItem {
        Objects.requireNonNull(description, "Line item description cannot be null");
        Objects.requireNonNull(unitPrice, "Unit price cannot be null");
        Objects.requireNonNull(taxRate, "Tax rate cannot be null");

        if (description.isBlank()) {
            throw new IllegalArgumentException("Line item description cannot be blank");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (taxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tax rate cannot be negative");
        }
    }

    public Money getLineTotal() {
        return unitPrice.multiply(quantity);
    }

    public Money getTaxAmount() {
        return getLineTotal().multiply(taxRate).divide(BigDecimal.valueOf(100));
    }

    public Money getTotalWithTax() {
        return getLineTotal().add(getTaxAmount());
    }
}
