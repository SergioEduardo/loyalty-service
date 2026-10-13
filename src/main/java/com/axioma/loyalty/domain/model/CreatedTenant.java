package com.axioma.loyalty.domain.model;

/** Negocio recién creado y el id de su aviso TenantChanged (para publicarlo después del commit). */
public record CreatedTenant(Tenant tenant, long eventId) {
}
