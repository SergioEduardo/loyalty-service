package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.UserAccount;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface UserRepository {

    /** Falla con USERNAME_TAKEN si el usuario ya existe en el negocio. */
    Mono<UserAccount> create(UserAccount user);

    Mono<UserAccount> update(UserAccount user);

    /** Vacío si no existe o pertenece a otro negocio. */
    Mono<UserAccount> findById(UUID tenantId, UUID userId);

    /** Vacío si no existe en ese negocio. */
    Mono<UserAccount> findByUsername(UUID tenantId, String username);
}
