package com.axioma.loyalty.adapter.in.web.auth;

/** toString nunca incluye el token. */
public record ActivationInspectRequest(String slug, String token) {

    @Override
    public String toString() {
        return "ActivationInspectRequest[slug=" + slug + ", token=***]";
    }
}
