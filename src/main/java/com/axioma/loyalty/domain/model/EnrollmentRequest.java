package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Entrada del alta ya validada y normalizada. toString omite teléfono, nombre y código. */
public record EnrollmentRequest(String slug, PhoneE164 phone, String holderName, UUID verificationId, String code) {

    @Override
    public String toString() {
        return "EnrollmentRequest[slug=" + slug + ", phone=***, holderName=***, verificationId=***, code=***]";
    }
}
