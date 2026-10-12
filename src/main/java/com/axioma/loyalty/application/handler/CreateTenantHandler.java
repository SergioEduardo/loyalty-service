package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.CreateTenantCommand;
import com.axioma.loyalty.application.validator.CreateTenantValidator;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.TenantChanged;
import com.axioma.loyalty.domain.port.out.TenantEventOutbox;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** Alta del negocio y su aviso TenantChanged en la misma transacción. */
@Service
@RequiredArgsConstructor
public class CreateTenantHandler implements Handler<CreateTenantCommand, Mono<Tenant>> {

    private final CreateTenantValidator validator;
    private final TenantRepository tenantRepository;
    private final TenantEventOutbox outbox;

    @Override
    @Transactional
    public Mono<Tenant> handle(CreateTenantCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> tenantRepository.create(toTenant(command))))
                .flatMap(tenant -> outbox.append(TenantChanged.from(tenant)).thenReturn(tenant));
    }

    private static Tenant toTenant(CreateTenantCommand command) {
        return new Tenant(
                UUID.randomUUID(),
                new Slug(command.slug()),
                command.name(),
                command.description(),
                null, // el logo se sube y normaliza en otra entrega
                command.brandColor(),
                command.pointsGoal(),
                command.rewardText(),
                command.cooldownSeconds(),
                command.undoWindowSeconds(),
                true);
    }
}
