package com.axioma.loyalty.adapter.in.web.auth;

/** toString nunca incluye el token ni la contraseña. */
public record ActivateRequest(String slug, String token, String password) {

    @Override
    public String toString() {
        return "ActivateRequest[slug=" + slug + ", token=***, password=***]";
    }
}
