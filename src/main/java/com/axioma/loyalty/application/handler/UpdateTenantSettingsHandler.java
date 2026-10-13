package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.UpdateTenantSettingsCommand;
import com.axioma.loyalty.application.validator.UpdateTenantSettingsValidator;
import com.axioma.loyalty.domain.model.TenantProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Meta de puntos, premio, espera entre escaneos y ventana para deshacer. */
@Service
@RequiredArgsConstructor
public class UpdateTenantSettingsHandler implements Handler<UpdateTenantSettingsCommand, Mono<TenantProfile>> {

    private final UpdateTenantSettingsValidator validator;
    private final TenantChangeWriter tenantChangeWriter;
    private final OutboxFastPath outboxFastPath;

    @Override
    public Mono<TenantProfile> handle(UpdateTenantSettingsCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> tenantChangeWriter.apply(command.tenantId(),
                        tenant -> tenant.withSettings(command.pointsGoal(), command.rewardText(),
                                command.cooldownSeconds(), command.undoWindowSeconds()))))
                .flatMap(update -> outboxFastPath.publish(update.eventId()).thenReturn(TenantProfile.from(update.after())));
    }
}
