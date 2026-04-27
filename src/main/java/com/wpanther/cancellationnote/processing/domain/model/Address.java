package com.wpanther.cancellationnote.processing.domain.model;

import java.util.Objects;

public record Address(String streetAddress, String city, String postalCode, String country) {

    public static Address of(String streetAddress, String city, String postalCode, String country) {
        return new Address(streetAddress, city, postalCode, country);
    }

    public Address {
        Objects.requireNonNull(country, "Country cannot be null");
        if (country.isBlank()) {
            throw new IllegalArgumentException("Country cannot be blank");
        }
    }
}
