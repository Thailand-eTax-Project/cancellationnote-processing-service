package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressTest {

    @Test
    void constructorWithAllFields() {
        Address address = new Address("123 Main St", "Bangkok", "10100", "TH");
        assertEquals("123 Main St", address.streetAddress());
        assertEquals("Bangkok", address.city());
        assertEquals("10100", address.postalCode());
        assertEquals("TH", address.country());
    }

    @Test
    void constructorWithNullCountryThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new Address("123 Main St", "Bangkok", "10100", null));
        assertEquals("Country cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithBlankCountryThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Address("123 Main St", "Bangkok", "10100", "  "));
        assertEquals("Country cannot be blank", ex.getMessage());
    }

    @Test
    void allowsNullStreetAddress() {
        Address address = new Address(null, "Bangkok", "10100", "TH");
        assertNull(address.streetAddress());
    }

    @Test
    void allowsNullCity() {
        Address address = new Address("123 Main St", null, "10100", "TH");
        assertNull(address.city());
    }

    @Test
    void allowsNullPostalCode() {
        Address address = new Address("123 Main St", "Bangkok", null, "TH");
        assertNull(address.postalCode());
    }

    @Test
    void factoryMethodOfCreatesAddress() {
        Address address = Address.of("456 Sukhumvit", "Bangkok", "10260", "TH");
        assertNotNull(address);
        assertEquals("456 Sukhumvit", address.streetAddress());
        assertEquals("Bangkok", address.city());
    }

    @Test
    void recordEquality() {
        Address a = new Address("123 Main St", "Bangkok", "10100", "TH");
        Address b = new Address("123 Main St", "Bangkok", "10100", "TH");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality() {
        Address a = new Address("123 Main St", "Bangkok", "10100", "TH");
        Address b = new Address("456 Sukhumvit", "Bangkok", "10100", "TH");
        assertNotEquals(a, b);
    }
}
