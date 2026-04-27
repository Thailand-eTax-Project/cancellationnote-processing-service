package com.wpanther.cancellationnote.processing.domain.model;

import java.util.Objects;

public record TaxIdentifier(String value, String scheme) {

    public static TaxIdentifier of(String value, String scheme) {
        return new TaxIdentifier(value, scheme);
    }

    public TaxIdentifier {
        Objects.requireNonNull(value, "Tax ID value cannot be null");
        Objects.requireNonNull(scheme, "Tax ID scheme cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("Tax ID value cannot be blank");
        }
    }

    @Override
    public String toString() {
        return String.format("%s:%s", scheme, value);
    }
}
