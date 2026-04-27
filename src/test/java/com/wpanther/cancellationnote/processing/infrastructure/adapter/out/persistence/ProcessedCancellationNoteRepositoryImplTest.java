package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProcessedCancellationNoteRepositoryImplTest {

    @Autowired
    private ProcessedCancellationNoteRepositoryImpl repository;

    private ProcessedCancellationNote createSampleNote(String sourceNoteId, String noteNumber) {
        TaxIdentifier sellerTaxId = TaxIdentifier.of("1234567890123", "VAT");
        Address sellerAddress = Address.of("123 Seller St", "Bangkok", "10100", "TH");
        Party seller = Party.of("Seller Co Ltd", sellerTaxId, sellerAddress, "seller@example.com");

        TaxIdentifier buyerTaxId = TaxIdentifier.of("9876543210987", "VAT");
        Address buyerAddress = Address.of("456 Buyer Ave", "Chiang Mai", "50200", "TH");
        Party buyer = Party.of("Buyer Corp", buyerTaxId, buyerAddress, "buyer@example.com");

        LineItem item = new LineItem("Item 1", 2, Money.of(new BigDecimal("100.00"), "THB"), new BigDecimal("7.00"));

        return ProcessedCancellationNote.builder()
                .id(new CancellationNoteId(UUID.randomUUID()))
                .sourceNoteId(sourceNoteId)
                .cancellationNoteNumber(noteNumber)
                .issueDate(LocalDate.of(2024, 1, 15))
                .cancellationDate(LocalDate.of(2024, 2, 1))
                .seller(seller)
                .buyer(buyer)
                .items(List.of(item))
                .currency("THB")
                .cancelledInvoiceNumber("INV001")
                .originalXml("<xml>test</xml>")
                .status(ProcessingStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 0))
                .completedAt(LocalDateTime.of(2024, 1, 15, 11, 0))
                .build();
    }

    @Test
    @DisplayName("save and findById should persist and retrieve")
    void saveAndFindById() {
        ProcessedCancellationNote note = createSampleNote("TIV0100000001001", "CN001");

        ProcessedCancellationNote saved = repository.save(note);

        Optional<ProcessedCancellationNote> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().sourceNoteId()).isEqualTo("TIV0100000001001");
        assertThat(found.get().cancellationNoteNumber()).isEqualTo("CN001");
        assertThat(found.get().seller().name()).isEqualTo("Seller Co Ltd");
        assertThat(found.get().buyer().name()).isEqualTo("Buyer Corp");
        assertThat(found.get().items()).hasSize(1);
    }

    @Test
    @DisplayName("findBySourceNoteId should find correct note")
    void findBySourceNoteId() {
        ProcessedCancellationNote note = createSampleNote("TIV0100000001001", "CN001");
        repository.save(note);

        Optional<ProcessedCancellationNote> found = repository.findBySourceNoteId("TIV0100000001001");
        assertThat(found).isPresent();
        assertThat(found.get().cancellationNoteNumber()).isEqualTo("CN001");

        Optional<ProcessedCancellationNote> notFound = repository.findBySourceNoteId("NONEXISTENT");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findByCancellationNoteNumber should find correct note")
    void findByCancellationNoteNumber() {
        ProcessedCancellationNote note = createSampleNote("TIV0100000001001", "CN001");
        repository.save(note);

        Optional<ProcessedCancellationNote> found = repository.findByCancellationNoteNumber("CN001");
        assertThat(found).isPresent();
        assertThat(found.get().sourceNoteId()).isEqualTo("TIV0100000001001");

        Optional<ProcessedCancellationNote> notFound = repository.findByCancellationNoteNumber("NONEXISTENT");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("deleteById should remove note")
    void deleteById() {
        ProcessedCancellationNote note = createSampleNote("TIV0100000001001", "CN001");
        ProcessedCancellationNote saved = repository.save(note);

        repository.deleteById(saved.id());

        Optional<ProcessedCancellationNote> found = repository.findById(saved.id());
        assertThat(found).isEmpty();
    }
}
