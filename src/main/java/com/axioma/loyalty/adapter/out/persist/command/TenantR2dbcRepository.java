package com.axioma.loyalty.adapter.out.persist.command;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TenantR2dbcRepository extends ReactiveCrudRepository<TenantEntity, UUID> {
}
