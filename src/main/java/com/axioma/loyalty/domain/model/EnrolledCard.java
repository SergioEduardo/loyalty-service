package com.axioma.loyalty.domain.model;

/** Resultado de dar la tarjeta: la de siempre si el teléfono ya tenía una (isNew = false). */
public record EnrolledCard(Card card, boolean isNew) {
}
