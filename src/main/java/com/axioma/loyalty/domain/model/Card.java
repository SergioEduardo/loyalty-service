package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Tarjeta de un cliente en un negocio (una por teléfono). toString omite el nombre. */
public record Card(UUID id, UUID tenantId, String holderName, int points) {

    @Override
    public String toString() {
        return "Card[id=" + id + ", tenantId=" + tenantId + ", holderName=***, points=" + points + "]";
    }
}
