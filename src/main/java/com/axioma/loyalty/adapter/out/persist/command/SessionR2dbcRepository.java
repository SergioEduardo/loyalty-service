package com.axioma.loyalty.adapter.out.persist.command;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SessionR2dbcRepository extends ReactiveCrudRepository<SessionEntity, UUID> {
}
