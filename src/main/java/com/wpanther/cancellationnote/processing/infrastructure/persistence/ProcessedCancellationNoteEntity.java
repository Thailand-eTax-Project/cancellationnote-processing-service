package com.wpanther.cancellationnote.processing.infrastructure.persistence;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "processed_cancellation_notes", indexes = {
    @Index(name = "idx_cancellation_note_number", columnList = "cancellation_note_number"),
    @Index(name = "idx_cancellation_source_note_id", columnList = "source_note_id"),
    @Index(name = "idx_cancellation_status", columnList = "status"),
    @Index(name = "idx_cancellation_issue_date", columnList = "issue_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedCancellationNoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_note_id", nullable = false, length = 100)
    private String sourceNoteId;

    @Column(name = "cancellation_note_number", nullable = false, length = 50)
    private String cancellationNoteNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "cancellation_date", nullable = false)
    private LocalDate cancellationDate;

    @Column(name = "cancelled_invoice_number", nullable = false, length = 50)
    private String cancelledInvoiceNumber;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTax;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "original_xml", nullable = false, columnDefinition = "TEXT")
    private String originalXml;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "cancellationNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CancellationNotePartyEntity> parties = new HashSet<>();

    @OneToMany(mappedBy = "cancellationNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    @Builder.Default
    private List<CancellationNoteLineItemEntity> lineItems = new ArrayList<>();

    public void addParty(CancellationNotePartyEntity party) {
        parties.add(party);
        party.setCancellationNote(this);
    }

    public void addLineItem(CancellationNoteLineItemEntity lineItem) {
        lineItems.add(lineItem);
        lineItem.setCancellationNote(this);
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
