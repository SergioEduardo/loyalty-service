package com.axioma.loyalty.adapter.in.web.enroll;

import com.axioma.loyalty.domain.model.CardEnrollment;
import java.time.Instant;

/**
 * {isNew, apple: {url, expiresAt}, google: {url} o null}. Sin cardId, nombre ni teléfono. google es
 * null si el wallet-service no dio enlace de Google (apagado, sin clase del negocio o falló); el de
 * Apple siempre va. toString omite las URLs.
 */
public record EnrollCardResponse(boolean isNew, WalletLink apple, GoogleWalletLink google) {

    public record WalletLink(String url, Instant expiresAt) {

        @Override
        public String toString() {
            return "WalletLink[url=***, expiresAt=" + expiresAt + "]";
        }
    }

    /** url = https://pay.google.com/gp/v/save/{jwt}; sin vencimiento propio. */
    public record GoogleWalletLink(String url) {

        @Override
        public String toString() {
            return "GoogleWalletLink[url=***]";
        }
    }

    static EnrollCardResponse from(CardEnrollment enrollment) {
        var pass = enrollment.pass();
        return new EnrollCardResponse(enrollment.isNew(), new WalletLink(pass.appleUrl(), pass.appleExpiresAt()),
                pass.hasGoogle() ? new GoogleWalletLink(pass.googleUrl()) : null);
    }
}
