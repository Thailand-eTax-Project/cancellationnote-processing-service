package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaxIdentifierTest {

    @Test
    void constructorWithValidValueAndScheme() {
        TaxIdentifier taxId = new TaxIdentifier("1234567890", "VAT");
        assertEquals("1234567890", taxId.value());
        assertEquals("VAT", taxId.scheme());
    }

    @Test
    void constructorWithNullValueThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new TaxIdentifier(null, "VAT"));
        assertEquals("Tax ID value cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithNullSchemeThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new TaxIdentifier("1234567890", null));
        assertEquals("Tax ID scheme cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithBlankValueThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TaxIdentifier("   ", "VAT"));
        assertEquals("Tax ID value cannot be blank", ex.getMessage());
    }

    @Test
    void allowsBlankScheme() {
        TaxIdentifier taxId = new TaxIdentifier("1234567890", "  ");
        assertEquals("  ", taxId.scheme());
    }

    @Test
    void factoryMethodOfCreatesTaxIdentifier() {
        TaxIdentifier taxId = TaxIdentifier.of("9876543210", "TIN");
        assertEquals("9876543210", taxId.value());
        assertEquals("TIN", taxId.scheme());
    }

    @Test
    void toStringReturnsSchemeColonValue() {
        TaxIdentifier taxId = new TaxIdentifier("1234567890", "VAT");
        assertEquals("VAT:1234567890", taxId.toString());
    }

    @Test
    void recordEquality() {
        TaxIdentifier a = new TaxIdentifier("1234567890", "VAT");
        TaxIdentifier b = new TaxIdentifier("1234567890", "VAT");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality() {
        TaxIdentifier a = new TaxIdentifier("1234567890", "VAT");
        TaxIdentifier b = new TaxIdentifier("0987654321", "VAT");
        assertNotEquals(a, b);
    }
}
