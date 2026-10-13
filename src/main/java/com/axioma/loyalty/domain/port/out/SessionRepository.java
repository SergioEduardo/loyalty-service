package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.NewSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface SessionRepository {

    Mono<Void> create(NewSession session);

    /**
     * Cambia el token de una sesión vigente (cuenta y negocio activos) y guarda el anterior en
     * el historial. Vacío si el token no es el vigente de ninguna sesión válida.
     */
    Mono<SessionOwner> rotate(String currentHash, String newHash, Instant expiresAt);

    /** Revoca la sesión del token, sea el vigente o uno ya rotado. Devuelve cuántas revocó. */
    Mono<Long> revokeByRefreshTokenHash(String refreshTokenHash);

    /** Rol actual de la cuenta si la sesión sigue vigente y cuenta y negocio activos; si no, vacío. */
    Mono<UserRole> findActiveRole(UUID sessionId, UUID tenantId, UUID userId);
}
