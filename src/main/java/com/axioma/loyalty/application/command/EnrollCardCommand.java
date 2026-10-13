package com.axioma.loyalty.application.command;

import java.util.Locale;

/** Dar la tarjeta (12.7). clientIp puede ser null. toString omite teléfono, nombre, código e IP. */
public record EnrollCardCommand(String slug, String phone, String name, String verificationId, String code,
                                String clientIp) {

    public EnrollCardCommand {
        slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "EnrollCardCommand[slug=" + slug + ", phone=***, name=***, verificationId=***, code=***, clientIp=***]";
    }
}
