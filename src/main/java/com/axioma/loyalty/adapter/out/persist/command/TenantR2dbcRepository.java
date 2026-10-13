package com.axioma.loyalty.adapter.out.persist.command;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface TenantR2dbcRepository extends ReactiveCrudRepository<TenantEntity, UUID> {

    Mono<TenantEntity> findBySlug(String slug);
}
