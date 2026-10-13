package com.axioma.loyalty.domain.model;

/** Intento ya contado: intentos con este incluido y el hash guardado para comparar. */
public record VerificationAttempt(int attempts, String codeHash) {

    @Override
    public String toString() {
        return "VerificationAttempt[attempts=" + attempts + ", codeHash=***]";
    }
}
