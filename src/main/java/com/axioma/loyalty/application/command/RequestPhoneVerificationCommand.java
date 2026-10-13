package com.axioma.loyalty.application.command;

import java.util.Locale;

/** Pedir un código para el teléfono (12.6). clientIp puede ser null. toString omite teléfono e IP. */
public record RequestPhoneVerificationCommand(String slug, String phone, String clientIp) {

    public RequestPhoneVerificationCommand {
        slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RequestPhoneVerificationCommand[slug=" + slug + ", phone=***, clientIp=***]";
    }
}
