package com.axioma.loyalty.domain.model;

import java.util.UUID;

/**
 * Lo que el adaptador de envío necesita para mandar el código por channel; validMinutes es la vigencia
 * que se le dice a la persona. toString omite teléfono y código.
 */
public record VerificationMessage(UUID tenantId, String businessName, UUID verificationId, PhoneE164 phone,
                                  String code, VerificationChannel channel, int validMinutes) {

    @Override
    public String toString() {
        return "VerificationMessage[tenantId=" + tenantId + ", verificationId=" + verificationId
                + ", channel=" + channel.value() + ", phone=***, code=***]";
    }
}
