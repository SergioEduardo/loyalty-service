package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Lo que el adaptador de envío necesita para mandar el código. toString omite teléfono y código. */
public record VerificationMessage(UUID tenantId, String businessName, UUID verificationId, PhoneE164 phone,
                                  String code) {

    @Override
    public String toString() {
        return "VerificationMessage[tenantId=" + tenantId + ", verificationId=" + verificationId
                + ", phone=***, code=***]";
    }
}
