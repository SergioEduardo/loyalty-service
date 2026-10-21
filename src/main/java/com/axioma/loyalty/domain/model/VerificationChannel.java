package com.axioma.loyalty.domain.model;

import java.util.Locale;
import java.util.Optional;

/** Medio por el que se envía el código de verificación. value() es el nombre en la API y en la configuración. */
public enum VerificationChannel {
    WHATSAPP("whatsapp"),
    SMS("sms");

    private final String value;

    VerificationChannel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Sin distinguir mayúsculas ni espacios alrededor; vacío si no es un canal conocido. */
    public static Optional<VerificationChannel> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        for (VerificationChannel channel : values()) {
            if (channel.value.equals(normalized)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}
