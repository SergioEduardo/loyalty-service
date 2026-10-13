package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.domain.model.ActivationLink;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** expiresAt en ISO-8601 (UTC); activationUrl se omite si no hay APP_WEB_BASE_URL. toString nunca incluye el token. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivationLinkResponse(
        String token,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
        String activationUrl) {

    static ActivationLinkResponse from(ActivationLink link) {
        return link == null ? null : new ActivationLinkResponse(link.token(), link.expiresAt(), link.url());
    }

    @Override
    public String toString() {
        return "ActivationLinkResponse[expiresAt=" + expiresAt + ", token=***]";
    }
}
