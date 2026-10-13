package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.application.validator.CreateUserValidator;
import com.axioma.loyalty.domain.model.CreatedUser;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Alta de cuenta. Con contraseña: una sola escritura, sin transacción, para que el hash no retenga
 * una conexión. Sin contraseña: la cuenta queda pendiente de activación y se emite su enlace
 * (ActivationLinkIssuer, en una transacción).
 */
@Service
@RequiredArgsConstructor
public class CreateUserHandler implements Handler<CreateUserCommand, Mono<CreatedUser>> {

    private final CreateUserValidator validator;
    private final ActiveTenantGuard activeTenantGuard;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final ActivationLinkIssuer activationLinkIssuer;

    @Override
    public Mono<CreatedUser> handle(CreateUserCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> activeTenantGuard.require(command.tenantId())))
                .flatMap(tenant -> command.password() == null
                        ? activationLinkIssuer.createPendingUser(tenant, newUser(command, null))
                        : passwordHasher.hash(command.password())
                                .flatMap(hash -> userRepository.create(newUser(command, hash)))
                                .map(user -> new CreatedUser(user, null)));
    }

    private static UserAccount newUser(CreateUserCommand command, String credentialHash) {
        return new UserAccount(
                UUID.randomUUID(),
                command.tenantId(),
                command.name(),
                command.username(),
                credentialHash,
                UserRole.valueOf(command.role()),
                true);
    }
}
