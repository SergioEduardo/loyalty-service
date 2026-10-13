package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.IssueActivationLinkCommand;
import com.axioma.loyalty.domain.model.ActivationLink;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Enlace nuevo para una cuenta: reenvío a una pendiente o cambio de contraseña por enlace para una
 * activa (su contraseña actual sirve hasta que use el enlace). Invalida los enlaces anteriores.
 */
@Service
@RequiredArgsConstructor
public class IssueActivationLinkHandler implements Handler<IssueActivationLinkCommand, Mono<ActivationLink>> {

    private final ActiveTenantGuard activeTenantGuard;
    private final UserRepository userRepository;
    private final ActivationLinkIssuer activationLinkIssuer;

    @Override
    public Mono<ActivationLink> handle(IssueActivationLinkCommand command) {
        if (command.tenantId() == null || command.userId() == null) {
            return Mono.error(new LoyaltyException(ErrorCode.INVALID_REQUEST));
        }
        return activeTenantGuard.require(command.tenantId())
                .flatMap(tenant -> userRepository.findById(command.tenantId(), command.userId())
                        .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.USER_NOT_FOUND)))
                        .flatMap(user -> user.active()
                                ? activationLinkIssuer.reissue(tenant, user)
                                : Mono.error(new LoyaltyException(ErrorCode.USER_INACTIVE))));
    }
}
