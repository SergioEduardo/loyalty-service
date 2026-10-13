package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.ResetPasswordCommand;
import com.axioma.loyalty.application.validator.ResetPasswordValidator;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.SessionRevoker;
import com.axioma.loyalty.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Cambia la contraseña, revoca las sesiones abiertas con la anterior e invalida los enlaces de
 * activación pendientes (si no, uno viejo podría reemplazar la contraseña nueva).
 */
@Service
@RequiredArgsConstructor
public class ResetPasswordHandler implements Handler<ResetPasswordCommand, Mono<Void>> {

    private final ResetPasswordValidator validator;
    private final ActiveTenantGuard activeTenantGuard;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final SessionRevoker sessionRevoker;
    private final ActivationTokenRepository activationTokens;

    @Override
    @Transactional
    public Mono<Void> handle(ResetPasswordCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> activeTenantGuard.require(command.tenantId())))
                .then(Mono.defer(() -> userRepository.findById(command.tenantId(), command.userId())))
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.USER_NOT_FOUND)))
                .flatMap(user -> passwordHasher.hash(command.newPassword()).map(user::withCredentialHash))
                .flatMap(userRepository::update)
                .then(Mono.defer(() -> sessionRevoker.revokeAllByUser(command.tenantId(), command.userId())))
                .then(Mono.defer(() -> activationTokens.invalidateAll(command.tenantId(), command.userId())));
    }
}
