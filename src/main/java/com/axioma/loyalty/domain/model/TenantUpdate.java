package com.axioma.loyalty.domain.model;

/** Resultado de cambiar un negocio: cómo estaba y cómo quedó. */
public record TenantUpdate(Tenant before, Tenant after) {

    public boolean changed() {
        return !before.equals(after);
    }
}
