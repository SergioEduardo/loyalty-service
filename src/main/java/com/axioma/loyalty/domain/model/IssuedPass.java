package com.axioma.loyalty.domain.model;

import java.time.Instant;

/**
 * Enlaces del pase: el de Apple (firmado, vence) siempre; el de Google ("Save to Google Wallet", lleva
 * un JWT firmado) solo si el wallet-service lo dio, si no googleUrl es null. toString omite las URLs.
 */
public record IssuedPass(String appleUrl, Instant appleExpiresAt, String googleUrl) {

    public boolean hasGoogle() {
        return googleUrl != null;
    }

    @Override
    public String toString() {
        return "IssuedPass[appleUrl=***, appleExpiresAt=" + appleExpiresAt + ", googleUrl="
                + (googleUrl == null ? "null" : "***") + "]";
    }
}
