package com.axioma.loyalty.application.query;

/** toString nunca incluye el token. */
public record AuthenticateStaffQuery(String accessToken) {

    @Override
    public String toString() {
        return "AuthenticateStaffQuery[accessToken=***]";
    }
}
