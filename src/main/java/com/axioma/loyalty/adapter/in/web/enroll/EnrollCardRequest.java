package com.axioma.loyalty.adapter.in.web.enroll;

/** POST /api/public/enroll/card. toString omite teléfono, nombre, verificationId y código. */
public record EnrollCardRequest(String slug, String phone, String name, String verificationId, String code) {

    @Override
    public String toString() {
        return "EnrollCardRequest[slug=" + slug + ", phone=***, name=***, verificationId=***, code=***]";
    }
}
