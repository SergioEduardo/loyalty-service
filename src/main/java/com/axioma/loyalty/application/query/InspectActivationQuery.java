package com.axioma.loyalty.application.query;

import java.util.Locale;

/** clientIp puede ser null. toString nunca incluye el token. */
public record InspectActivationQuery(String slug, String token, String clientIp) {

    public InspectActivationQuery {
        slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
        token = token == null ? null : token.strip();
    }

    @Override
    public String toString() {
        return "InspectActivationQuery[slug=" + slug + ", token=***]";
    }
}
