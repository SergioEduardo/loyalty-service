package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserR2dbcRepository repository;

    @Override
    public Mono<UserAccount> create(UserAccount user) {
        return repository.save(toEntity(user, true))
                .map(UserPersistenceAdapter::toDomain)
                .onErrorMap(DuplicateKeyException.class,
                        e -> new LoyaltyException(ErrorCode.USERNAME_TAKEN, e));
    }

    @Override
    public Mono<UserAccount> update(UserAccount user) {
        return repository.save(toEntity(user, false)).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Mono<UserAccount> findById(UUID tenantId, UUID userId) {
        return repository.findByTenantIdAndId(tenantId, userId).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Mono<UserAccount> findByUsername(UUID tenantId, String username) {
        return repository.findByTenantIdAndUsername(tenantId, username).map(UserPersistenceAdapter::toDomain);
    }

    private static UserEntity toEntity(UserAccount user, boolean isNew) {
        return new UserEntity(
                user.id(),
                user.tenantId(),
                user.name(),
                user.username(),
                user.credentialHash(),
                user.role().name(),
                user.active(),
                isNew);
    }

    private static UserAccount toDomain(UserEntity entity) {
        return new UserAccount(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getUsername(),
                entity.getCredentialHash(),
                UserRole.valueOf(entity.getRole()),
                entity.isActive());
    }
}
