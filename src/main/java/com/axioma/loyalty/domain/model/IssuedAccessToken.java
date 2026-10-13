package com.axioma.loyalty.domain.model;

public record IssuedAccessToken(String value, long expiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedAccessToken[value=***, expiresInSeconds=" + expiresInSeconds + "]";
    }
}
