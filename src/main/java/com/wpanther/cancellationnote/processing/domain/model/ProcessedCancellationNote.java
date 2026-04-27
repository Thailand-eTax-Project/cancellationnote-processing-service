package com.wpanther.cancellationnote.processing.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ProcessedCancellationNote {

    private final CancellationNoteId id;
    private final String sourceNoteId;
    private final String cancellationNoteNumber;
    private final LocalDate issueDate;
    private final LocalDate cancellationDate;
    private final Party seller;
    private final Party buyer;
    private final List<LineItem> items;
    private final String currency;
    private final String cancelledInvoiceNumber;
    private final String originalXml;

    private ProcessingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    private transient Money cachedSubtotal;
    private transient Money cachedTotalTax;
    private transient Money cachedTotal;

    private ProcessedCancellationNote(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Cancellation Note ID is required");
        this.sourceNoteId = Objects.requireNonNull(builder.sourceNoteId, "Source note ID is required");
        this.cancellationNoteNumber = Objects.requireNonNull(builder.cancellationNoteNumber, "Cancellation note number is required");
        this.issueDate = Objects.requireNonNull(builder.issueDate, "Issue date is required");
        this.cancellationDate = Objects.requireNonNull(builder.cancellationDate, "Cancellation date is required");
        this.seller = Objects.requireNonNull(builder.seller, "Seller is required");
        this.buyer = Objects.requireNonNull(builder.buyer, "Buyer is required");
        this.items = new ArrayList<>(Objects.requireNonNull(builder.items, "Items are required"));
        this.currency = Objects.requireNonNull(builder.currency, "Currency is required");
        this.cancelledInvoiceNumber = Objects.requireNonNull(builder.cancelledInvoiceNumber, "Cancelled invoice number is required");
        this.originalXml = Objects.requireNonNull(builder.originalXml, "Original XML is required");
        this.status = builder.status != null ? builder.status : ProcessingStatus.PENDING;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.completedAt = builder.completedAt;
        this.errorMessage = builder.errorMessage;

        validateInvariant();
    }

    private void validateInvariant() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Cancellation note must have at least one line item");
        }

        if (cancellationDate.isBefore(issueDate)) {
            throw new IllegalStateException("Cancellation date cannot be before issue date");
        }

        if (currency.length() != 3) {
            throw new IllegalStateException("Currency must be a 3-letter ISO code");
        }

        items.forEach(item -> {
            if (!item.unitPrice().currency().equals(currency)) {
                throw new IllegalStateException(
                    String.format("Line item currency %s does not match note currency %s",
                        item.unitPrice().currency(), currency)
                );
            }
        });
    }

    public CancellationNoteId id() {
        return id;
    }

    public String sourceNoteId() {
        return sourceNoteId;
    }

    public String cancellationNoteNumber() {
        return cancellationNoteNumber;
    }

    public LocalDate issueDate() {
        return issueDate;
    }

    public LocalDate cancellationDate() {
        return cancellationDate;
    }

    public Party seller() {
        return seller;
    }

    public Party buyer() {
        return buyer;
    }

    public List<LineItem> items() {
        return Collections.unmodifiableList(items);
    }

    public String currency() {
        return currency;
    }

    public String cancelledInvoiceNumber() {
        return cancelledInvoiceNumber;
    }

    public String originalXml() {
        return originalXml;
    }

    public ProcessingStatus status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Money getSubtotal() {
        if (cachedSubtotal == null) {
            cachedSubtotal = items.stream()
                .map(LineItem::getLineTotal)
                .reduce(Money.zero(currency), Money::add);
        }
        return cachedSubtotal;
    }

    public Money getTotalTax() {
        if (cachedTotalTax == null) {
            cachedTotalTax = items.stream()
                .map(LineItem::getTaxAmount)
                .reduce(Money.zero(currency), Money::add);
        }
        return cachedTotalTax;
    }

    public Money getTotal() {
        if (cachedTotal == null) {
            cachedTotal = items.stream()
                .map(LineItem::getTotalWithTax)
                .reduce(Money.zero(currency), Money::add);
        }
        return cachedTotal;
    }

    public void markProcessing() {
        this.status = ProcessingStatus.PROCESSING;
        this.completedAt = null;
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = ProcessingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CancellationNoteId id;
        private String sourceNoteId;
        private String cancellationNoteNumber;
        private LocalDate issueDate;
        private LocalDate cancellationDate;
        private Party seller;
        private Party buyer;
        private List<LineItem> items;
        private String currency;
        private String cancelledInvoiceNumber;
        private String originalXml;
        private ProcessingStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private String errorMessage;

        public Builder id(CancellationNoteId id) {
            this.id = id;
            return this;
        }

        public Builder sourceNoteId(String sourceNoteId) {
            this.sourceNoteId = sourceNoteId;
            return this;
        }

        public Builder cancellationNoteNumber(String cancellationNoteNumber) {
            this.cancellationNoteNumber = cancellationNoteNumber;
            return this;
        }

        public Builder issueDate(LocalDate issueDate) {
            this.issueDate = issueDate;
            return this;
        }

        public Builder cancellationDate(LocalDate cancellationDate) {
            this.cancellationDate = cancellationDate;
            return this;
        }

        public Builder seller(Party seller) {
            this.seller = seller;
            return this;
        }

        public Builder buyer(Party buyer) {
            this.buyer = buyer;
            return this;
        }

        public Builder items(List<LineItem> items) {
            this.items = items;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder cancelledInvoiceNumber(String cancelledInvoiceNumber) {
            this.cancelledInvoiceNumber = cancelledInvoiceNumber;
            return this;
        }

        public Builder originalXml(String originalXml) {
            this.originalXml = originalXml;
            return this;
        }

        public Builder status(ProcessingStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ProcessedCancellationNote build() {
            return new ProcessedCancellationNote(this);
        }
    }
}
