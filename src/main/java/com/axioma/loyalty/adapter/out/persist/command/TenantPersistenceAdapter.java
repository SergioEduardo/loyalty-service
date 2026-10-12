package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TenantPersistenceAdapter implements TenantRepository {

    private final TenantR2dbcRepository repository;

    @Override
    public Mono<Tenant> create(Tenant tenant) {
        return repository.save(toEntity(tenant, true))
                .map(TenantPersistenceAdapter::toDomain)
                .onErrorMap(DuplicateKeyException.class,
                        e -> new LoyaltyException(ErrorCode.SLUG_ALREADY_EXISTS, e));
    }

    @Override
    public Mono<Tenant> findById(UUID tenantId) {
        return repository.findById(tenantId).map(TenantPersistenceAdapter::toDomain);
    }

    private static TenantEntity toEntity(Tenant tenant, boolean isNew) {
        return new TenantEntity(
                tenant.id(),
                tenant.slug().value(),
                tenant.name(),
                tenant.description(),
                tenant.logoUrl(),
                tenant.brandColor(),
                tenant.pointsGoal(),
                tenant.rewardText(),
                tenant.cooldownSeconds(),
                tenant.undoWindowSeconds(),
                tenant.active(),
                isNew);
    }

    private static Tenant toDomain(TenantEntity entity) {
        return new Tenant(
                entity.getId(),
                new Slug(entity.getSlug()),
                entity.getBusinessName(),
                entity.getDescription(),
                entity.getLogoUrl(),
                entity.getBrandColor(),
                entity.getPointsGoal(),
                entity.getRewardText(),
                entity.getCooldownSeconds(),
                entity.getUndoWindowSeconds(),
                entity.isActive());
    }
}
