package com.axioma.loyalty.domain.model;

/** Respuesta del alta: si la tarjeta es nueva y el enlace del pase. Nunca el cardId, nombre ni teléfono. */
public record CardEnrollment(boolean isNew, IssuedPass pass) {
}
