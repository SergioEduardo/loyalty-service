package com.axioma.loyalty.adapter.in.web.enroll;

/** POST /api/public/enroll/verification. channel (whatsapp | sms) es opcional. toString omite el teléfono. */
public record PhoneVerificationRequest(String slug, String phone, String channel) {

    @Override
    public String toString() {
        return "PhoneVerificationRequest[slug=" + slug + ", phone=***, channel=" + channel + "]";
    }
}
