package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.CancellationNotePartyEntity.PartyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationNotePartyEntityTest {

    @Test
    @DisplayName("setters should work correctly")
    void settersShouldWork() {
        CancellationNotePartyEntity entity = new CancellationNotePartyEntity();
        UUID id = UUID.randomUUID();
        ProcessedCancellationNoteEntity parent = ProcessedCancellationNoteEntity.builder().build();

        entity.setId(id);
        entity.setCancellationNote(parent);
        entity.setPartyType(PartyType.SELLER);
        entity.setName("Test Company");
        entity.setTaxId("1234567890123");
        entity.setTaxIdScheme("VAT");
        entity.setStreetAddress("123 Test St");
        entity.setCity("Bangkok");
        entity.setPostalCode("10100");
        entity.setCountry("TH");
        entity.setEmail("test@example.com");

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCancellationNote()).isSameAs(parent);
        assertThat(entity.getPartyType()).isEqualTo(PartyType.SELLER);
        assertThat(entity.getName()).isEqualTo("Test Company");
        assertThat(entity.getTaxId()).isEqualTo("1234567890123");
        assertThat(entity.getTaxIdScheme()).isEqualTo("VAT");
        assertThat(entity.getStreetAddress()).isEqualTo("123 Test St");
        assertThat(entity.getCity()).isEqualTo("Bangkok");
        assertThat(entity.getPostalCode()).isEqualTo("10100");
        assertThat(entity.getCountry()).isEqualTo("TH");
        assertThat(entity.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("PartyType should have SELLER and BUYER values")
    void partyTypeShouldHaveValues() {
        assertThat(PartyType.values()).containsExactly(PartyType.SELLER, PartyType.BUYER);
        assertThat(PartyType.valueOf("SELLER")).isEqualTo(PartyType.SELLER);
        assertThat(PartyType.valueOf("BUYER")).isEqualTo(PartyType.BUYER);
    }

    @Test
    @DisplayName("builder should create entity with all fields")
    void builderShouldCreateEntity() {
        CancellationNotePartyEntity entity = CancellationNotePartyEntity.builder()
                .id(UUID.randomUUID())
                .partyType(PartyType.BUYER)
                .name("Buyer Co")
                .taxId("9876543210987")
                .taxIdScheme("VAT")
                .streetAddress("456 Buyer Ave")
                .city("Chiang Mai")
                .postalCode("50200")
                .country("TH")
                .email("buyer@example.com")
                .build();

        assertThat(entity.getPartyType()).isEqualTo(PartyType.BUYER);
        assertThat(entity.getName()).isEqualTo("Buyer Co");
        assertThat(entity.getCountry()).isEqualTo("TH");
    }
}
