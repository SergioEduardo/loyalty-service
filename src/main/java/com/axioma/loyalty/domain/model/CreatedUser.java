package com.axioma.loyalty.domain.model;

/** Cuenta recién creada; activation solo viene si se creó sin contraseña (pendiente de activación). */
public record CreatedUser(UserAccount user, ActivationLink activation) {
}
