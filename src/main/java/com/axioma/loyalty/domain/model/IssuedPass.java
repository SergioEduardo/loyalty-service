package com.axioma.loyalty.domain.model;

import java.time.Instant;

/** Enlace firmado de descarga del pase de Apple. toString omite la URL (lleva la firma). */
public record IssuedPass(String appleUrl, Instant appleExpiresAt) {

    @Override
    public String toString() {
        return "IssuedPass[appleUrl=***, appleExpiresAt=" + appleExpiresAt + "]";
    }
}
