package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessedCancellationNoteMapperTest {

    private ProcessedCancellationNoteMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProcessedCancellationNoteMapper();
    }

    private ProcessedCancellationNote createSampleDomain() {
        TaxIdentifier sellerTaxId = TaxIdentifier.of("1234567890123", "VAT");
        Address sellerAddress = Address.of("123 Seller St", "Bangkok", "10100", "TH");
        Party seller = Party.of("Seller Co Ltd", sellerTaxId, sellerAddress, "seller@example.com");

        TaxIdentifier buyerTaxId = TaxIdentifier.of("9876543210987", "VAT");
        Address buyerAddress = Address.of("456 Buyer Ave", "Chiang Mai", "50200", "TH");
        Party buyer = Party.of("Buyer Corp", buyerTaxId, buyerAddress, "buyer@example.com");

        LineItem item1 = new LineItem("Item 1", 2, Money.of(new BigDecimal("100.00"), "THB"), new BigDecimal("7.00"));
        LineItem item2 = new LineItem("Item 2", 1, Money.of(new BigDecimal("200.00"), "THB"), new BigDecimal("7.00"));

        return ProcessedCancellationNote.builder()
                .id(new CancellationNoteId(UUID.randomUUID()))
                .sourceNoteId("TIV0100000001001")
                .cancellationNoteNumber("CN001")
                .issueDate(LocalDate.of(2024, 1, 15))
                .cancellationDate(LocalDate.of(2024, 2, 1))
                .seller(seller)
                .buyer(buyer)
                .items(List.of(item1, item2))
                .currency("THB")
                .cancelledInvoiceNumber("INV001")
                .originalXml("<xml>test</xml>")
                .status(ProcessingStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 0))
                .completedAt(LocalDateTime.of(2024, 1, 15, 11, 0))
                .build();
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntityTests {

        @Test
        @DisplayName("should map all scalar fields correctly")
        void shouldMapScalarFields() {
            ProcessedCancellationNote domain = createSampleDomain();

            ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

            assertThat(entity.getSourceNoteId()).isEqualTo("TIV0100000001001");
            assertThat(entity.getCancellationNoteNumber()).isEqualTo("CN001");
            assertThat(entity.getIssueDate()).isEqualTo(LocalDate.of(2024, 1, 15));
            assertThat(entity.getCancellationDate()).isEqualTo(LocalDate.of(2024, 2, 1));
            assertThat(entity.getCancelledInvoiceNumber()).isEqualTo("INV001");
            assertThat(entity.getCurrency()).isEqualTo("THB");
            assertThat(entity.getOriginalXml()).isEqualTo("<xml>test</xml>");
            assertThat(entity.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
            assertThat(entity.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 0));
            assertThat(entity.getCompletedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 11, 0));
            assertThat(entity.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("should map calculated totals")
        void shouldMapCalculatedTotals() {
            ProcessedCancellationNote domain = createSampleDomain();

            ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

            // item1: 2 * 100 = 200, tax = 200 * 0.07 = 14, total = 214
            // item2: 1 * 200 = 200, tax = 200 * 0.07 = 14, total = 214
            // subtotal = 400, totalTax = 28, total = 428
            assertThat(entity.getSubtotal()).isEqualByComparingTo("400.00");
            assertThat(entity.getTotalTax()).isEqualByComparingTo("28.00");
            assertThat(entity.getTotal()).isEqualByComparingTo("428.00");
        }

        @Test
        @DisplayName("should map parties with correct types and back-reference")
        void shouldMapParties() {
            ProcessedCancellationNote domain = createSampleDomain();

            ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

            assertThat(entity.getParties()).hasSize(2);

            CancellationNotePartyEntity sellerParty = entity.getParties().stream()
                    .filter(p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.SELLER)
                    .findFirst().orElseThrow();
            assertThat(sellerParty.getName()).isEqualTo("Seller Co Ltd");
            assertThat(sellerParty.getTaxId()).isEqualTo("1234567890123");
            assertThat(sellerParty.getTaxIdScheme()).isEqualTo("VAT");
            assertThat(sellerParty.getStreetAddress()).isEqualTo("123 Seller St");
            assertThat(sellerParty.getCity()).isEqualTo("Bangkok");
            assertThat(sellerParty.getPostalCode()).isEqualTo("10100");
            assertThat(sellerParty.getCountry()).isEqualTo("TH");
            assertThat(sellerParty.getEmail()).isEqualTo("seller@example.com");
            assertThat(sellerParty.getCancellationNote()).isSameAs(entity);

            CancellationNotePartyEntity buyerParty = entity.getParties().stream()
                    .filter(p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.BUYER)
                    .findFirst().orElseThrow();
            assertThat(buyerParty.getName()).isEqualTo("Buyer Corp");
            assertThat(buyerParty.getTaxId()).isEqualTo("9876543210987");
            assertThat(buyerParty.getCancellationNote()).isSameAs(entity);
        }

        @Test
        @DisplayName("should map line items with correct numbers and back-reference")
        void shouldMapLineItems() {
            ProcessedCancellationNote domain = createSampleDomain();

            ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

            assertThat(entity.getLineItems()).hasSize(2);

            CancellationNoteLineItemEntity item1 = entity.getLineItems().get(0);
            assertThat(item1.getLineNumber()).isEqualTo(1);
            assertThat(item1.getDescription()).isEqualTo("Item 1");
            assertThat(item1.getQuantity()).isEqualTo(2);
            assertThat(item1.getUnitPrice()).isEqualByComparingTo("100.00");
            assertThat(item1.getTaxRate()).isEqualByComparingTo("7.00");
            assertThat(item1.getLineTotal()).isEqualByComparingTo("200.00");
            assertThat(item1.getTaxAmount()).isEqualByComparingTo("14.00");
            assertThat(item1.getCancellationNote()).isSameAs(entity);

            CancellationNoteLineItemEntity item2 = entity.getLineItems().get(1);
            assertThat(item2.getLineNumber()).isEqualTo(2);
            assertThat(item2.getDescription()).isEqualTo("Item 2");
            assertThat(item2.getQuantity()).isEqualTo(1);
            assertThat(item2.getUnitPrice()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("should use domain id when present")
        void shouldUseDomainId() {
            UUID id = UUID.randomUUID();
            ProcessedCancellationNote domain = createSampleDomain();

            ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

            assertThat(entity.getId()).isEqualTo(domain.id().value());
        }
    }

    @Nested
    @DisplayName("toDomain")
    class ToDomainTests {

        @Test
        @DisplayName("should round-trip domain object through entity and back")
        void shouldRoundTrip() {
            ProcessedCancellationNote original = createSampleDomain();

            ProcessedCancellationNoteEntity entity = mapper.toEntity(original);
            ProcessedCancellationNote restored = mapper.toDomain(entity);

            assertThat(restored.id()).isEqualTo(original.id());
            assertThat(restored.sourceNoteId()).isEqualTo(original.sourceNoteId());
            assertThat(restored.cancellationNoteNumber()).isEqualTo(original.cancellationNoteNumber());
            assertThat(restored.issueDate()).isEqualTo(original.issueDate());
            assertThat(restored.cancellationDate()).isEqualTo(original.cancellationDate());
            assertThat(restored.cancelledInvoiceNumber()).isEqualTo(original.cancelledInvoiceNumber());
            assertThat(restored.currency()).isEqualTo(original.currency());
            assertThat(restored.originalXml()).isEqualTo(original.originalXml());
            assertThat(restored.status()).isEqualTo(original.status());
            assertThat(restored.errorMessage()).isEqualTo(original.errorMessage());
            assertThat(restored.seller().name()).isEqualTo(original.seller().name());
            assertThat(restored.buyer().name()).isEqualTo(original.buyer().name());
            assertThat(restored.items()).hasSize(original.items().size());
        }

        @Test
        @DisplayName("should throw when seller party is missing")
        void shouldThrowWhenSellerMissing() {
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
                    .createdAt(LocalDateTime.now())
                    .parties(new java.util.HashSet<>())
                    .lineItems(new ArrayList<>())
                    .build();

            assertThatThrownBy(() -> mapper.toDomain(entity))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No SELLER party found");
        }

        @Test
        @DisplayName("should throw when buyer party is missing")
        void shouldThrowWhenBuyerMissing() {
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
                    .createdAt(LocalDateTime.now())
                    .parties(new java.util.HashSet<>())
                    .lineItems(new ArrayList<>())
                    .build();

            assertThatThrownBy(() -> mapper.toDomain(entity))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No SELLER party found");
        }
    }
}
