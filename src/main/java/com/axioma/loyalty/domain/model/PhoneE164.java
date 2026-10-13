package com.axioma.loyalty.domain.model;

import java.util.Objects;

/** Teléfono ya normalizado a E.164 (+525512345678). toString nunca lo muestra. */
public record PhoneE164(String value) {

    public PhoneE164 {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return "PhoneE164[***]";
    }
}
