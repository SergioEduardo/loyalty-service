package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.CreateTenantCommand;
import com.axioma.loyalty.application.validator.CreateTenantValidator;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Alta del negocio con su aviso TenantChanged (misma transacción); después del commit, lo publica. */
@Service
@RequiredArgsConstructor
public class CreateTenantHandler implements Handler<CreateTenantCommand, Mono<Tenant>> {

    private final CreateTenantValidator validator;
    private final TenantCreationWriter tenantCreationWriter;
    private final OutboxFastPath outboxFastPath;

    @Override
    public Mono<Tenant> handle(CreateTenantCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> tenantCreationWriter.create(toTenant(command))))
                .flatMap(created -> outboxFastPath.publish(created.eventId()).thenReturn(created.tenant()));
    }

    private static Tenant toTenant(CreateTenantCommand command) {
        return new Tenant(
                UUID.randomUUID(),
                new Slug(command.slug()),
                command.name(),
                command.description(),
                null, // el logo lo sube el dueño (UploadTenantLogoHandler)
                null,
                command.brandColor(),
                command.pointsGoal(),
                command.rewardText(),
                command.cooldownSeconds(),
                command.undoWindowSeconds(),
                true);
    }
}
