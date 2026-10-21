package com.axioma.loyalty.application.command;

import java.util.Locale;

/**
 * Pedir un código para el teléfono (12.6). channel (whatsapp | sms) es opcional: null o vacío usa el
 * canal por defecto. clientIp puede ser null. toString omite teléfono e IP.
 */
public record RequestPhoneVerificationCommand(String slug, String phone, String channel, String clientIp) {

    public RequestPhoneVerificationCommand {
        slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RequestPhoneVerificationCommand[slug=" + slug + ", phone=***, channel=" + channel + ", clientIp=***]";
    }
}
