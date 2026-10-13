package com.axioma.loyalty.adapter.in.web.enroll;

/** POST /api/public/enroll/verification. toString omite el teléfono. */
public record PhoneVerificationRequest(String slug, String phone) {

    @Override
    public String toString() {
        return "PhoneVerificationRequest[slug=" + slug + ", phone=***]";
    }
}
