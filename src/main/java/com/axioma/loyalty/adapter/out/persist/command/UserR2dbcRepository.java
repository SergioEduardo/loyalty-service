package com.axioma.loyalty.adapter.out.persist.command;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserR2dbcRepository extends ReactiveCrudRepository<UserEntity, UUID> {

    Mono<UserEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Mono<UserEntity> findByTenantIdAndUsername(UUID tenantId, String username);
}
