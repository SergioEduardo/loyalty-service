package com.axioma.loyalty.application.query;

import java.util.UUID;

/** El negocio sale del token. */
public record GetTenantProfileQuery(UUID tenantId) {
}
