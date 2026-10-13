package com.axioma.loyalty.adapter.in.web.auth;

/** Cuerpo de refresh y logout. toString nunca incluye el token. */
public record RefreshTokenRequest(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
