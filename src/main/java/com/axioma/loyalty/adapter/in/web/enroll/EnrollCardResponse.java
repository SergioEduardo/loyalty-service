package com.axioma.loyalty.adapter.in.web.enroll;

import com.axioma.loyalty.domain.model.CardEnrollment;
import java.time.Instant;

/**
 * {isNew, apple: {url, expiresAt}, google: null}. Sin cardId, nombre ni teléfono. google va en null
 * hasta conectar Google Wallet.
 */
public record EnrollCardResponse(boolean isNew, WalletLink apple, WalletLink google) {

    public record WalletLink(String url, Instant expiresAt) {
    }

    static EnrollCardResponse from(CardEnrollment enrollment) {
        var pass = enrollment.pass();
        return new EnrollCardResponse(enrollment.isNew(), new WalletLink(pass.appleUrl(), pass.appleExpiresAt()), null);
    }
}
