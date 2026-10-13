package com.axioma.loyalty.domain.model;

/**
 * Resultado de cambiar un negocio: cómo estaba y cómo quedó. eventId es el id del TenantChanged
 * escrito en el outbox, o null si nada cambió (no se avisa).
 */
public record TenantUpdate(Tenant before, Tenant after, Long eventId) {

    public static TenantUpdate unchanged(Tenant tenant) {
        return new TenantUpdate(tenant, tenant, null);
    }

    public boolean changed() {
        return !before.equals(after);
    }
}
