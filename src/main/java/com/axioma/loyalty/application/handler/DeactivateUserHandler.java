package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.DeactivateUserCommand;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.SessionRevoker;
import com.axioma.loyalty.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** Desactiva la cuenta y revoca todas sus sesiones. Repetirlo no falla. */
@Service
@RequiredArgsConstructor
public class DeactivateUserHandler implements Handler<DeactivateUserCommand, Mono<Void>> {

    private final ActiveTenantGuard activeTenantGuard;
    private final UserRepository userRepository;
    private final SessionRevoker sessionRevoker;

    @Override
    @Transactional
    public Mono<Void> handle(DeactivateUserCommand command) {
        return activeTenantGuard.require(command.tenantId())
                .then(Mono.defer(() -> userRepository.findById(command.tenantId(), command.userId())))
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.USER_NOT_FOUND)))
                .map(UserAccount::deactivate)
                .flatMap(userRepository::update)
                .then(Mono.defer(() -> sessionRevoker.revokeAllByUser(command.tenantId(), command.userId())))
                .then();
    }
}
