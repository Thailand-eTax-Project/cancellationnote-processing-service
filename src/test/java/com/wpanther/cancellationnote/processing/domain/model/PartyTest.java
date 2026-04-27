package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PartyTest {

    private static final TaxIdentifier TAX_ID = new TaxIdentifier("1234567890", "VAT");
    private static final Address ADDRESS = new Address("123 Main St", "Bangkok", "10100", "TH");

    @Test
    void constructorWithAllFields() {
        Party party = new Party("Acme Corp", TAX_ID, ADDRESS, "info@acme.com");
        assertEquals("Acme Corp", party.name());
        assertEquals(TAX_ID, party.taxIdentifier());
        assertEquals(ADDRESS, party.address());
        assertEquals("info@acme.com", party.email());
    }

    @Test
    void constructorWithNullNameThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new Party(null, TAX_ID, ADDRESS, "info@acme.com"));
        assertEquals("Party name cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithBlankNameThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Party("  ", TAX_ID, ADDRESS, "info@acme.com"));
        assertEquals("Party name cannot be blank", ex.getMessage());
    }

    @Test
    void constructorWithNullTaxIdentifierThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new Party("Acme Corp", null, ADDRESS, "info@acme.com"));
        assertEquals("Tax identifier cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithNullAddressThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new Party("Acme Corp", TAX_ID, null, "info@acme.com"));
        assertEquals("Address cannot be null", ex.getMessage());
    }

    @Test
    void allowsNullEmail() {
        Party party = new Party("Acme Corp", TAX_ID, ADDRESS, null);
        assertNull(party.email());
    }

    @Test
    void factoryMethodOfCreatesParty() {
        Party party = Party.of("Acme Corp", TAX_ID, ADDRESS, "info@acme.com");
        assertNotNull(party);
        assertEquals("Acme Corp", party.name());
    }

    @Test
    void recordEquality() {
        Party a = new Party("Acme Corp", TAX_ID, ADDRESS, "info@acme.com");
        Party b = new Party("Acme Corp", TAX_ID, ADDRESS, "info@acme.com");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality() {
        Party a = new Party("Acme Corp", TAX_ID, ADDRESS, "info@acme.com");
        Party b = new Party("Other Corp", TAX_ID, ADDRESS, "info@acme.com");
        assertNotEquals(a, b);
    }
}
