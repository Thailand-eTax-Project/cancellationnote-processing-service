package com.wpanther.cancellationnote.processing.domain.model;

import java.util.Objects;

public record Party(String name, TaxIdentifier taxIdentifier, Address address, String email) {

    public static Party of(String name, TaxIdentifier taxIdentifier, Address address, String email) {
        return new Party(name, taxIdentifier, address, email);
    }

    public Party {
        Objects.requireNonNull(name, "Party name cannot be null");
        Objects.requireNonNull(taxIdentifier, "Tax identifier cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Party name cannot be blank");
        }
    }
}
