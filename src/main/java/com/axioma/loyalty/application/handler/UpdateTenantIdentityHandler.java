package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.UpdateTenantIdentityCommand;
import com.axioma.loyalty.application.validator.UpdateTenantIdentityValidator;
import com.axioma.loyalty.domain.model.TenantProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Nombre, descripción y color del negocio; avisa TenantChanged si algo cambió. */
@Service
@RequiredArgsConstructor
public class UpdateTenantIdentityHandler implements Handler<UpdateTenantIdentityCommand, Mono<TenantProfile>> {

    private final UpdateTenantIdentityValidator validator;
    private final TenantChangeWriter tenantChangeWriter;
    private final OutboxFastPath outboxFastPath;

    @Override
    public Mono<TenantProfile> handle(UpdateTenantIdentityCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> tenantChangeWriter.apply(command.tenantId(),
                        tenant -> tenant.withIdentity(command.name(), command.description(), command.brandColor()))))
                .flatMap(update -> outboxFastPath.publish(update.eventId()).thenReturn(TenantProfile.from(update.after())));
    }
}
