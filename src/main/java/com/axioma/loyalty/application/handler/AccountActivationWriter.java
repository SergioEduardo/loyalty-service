package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import com.axioma.loyalty.domain.port.out.SessionRevoker;
import com.axioma.loyalty.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Efectos de activar en una sola transacción: consume el enlace (si sigue válido), guarda el hash
 * de la contraseña (la cuenta deja de estar pendiente), revoca todas las sesiones previas e invalida
 * los demás enlaces de la cuenta. Vacío si el enlace ya no es válido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountActivationWriter {

    private final ActivationTokenRepository activationTokens;
    private final UserRepository userRepository;
    private final SessionRevoker sessionRevoker;

    @Transactional
    public Mono<UserAccount> activate(String slug, String tokenHash, String credentialHash) {
        return activationTokens.consume(slug, tokenHash)
                .flatMap(target -> userRepository.findById(target.tenantId(), target.userId()))
                .flatMap(user -> userRepository.update(user.withCredentialHash(credentialHash)))
                .flatMap(user -> sessionRevoker.revokeAllByUser(user.tenantId(), user.id())
                        .flatMap(revoked -> activationTokens.invalidateAll(user.tenantId(), user.id())
                                .then(Mono.fromRunnable(() -> log.info(
                                        "Account activated tenantId={} userId={} revokedSessions={}",
                                        user.tenantId(), user.id(), revoked))))
                        .thenReturn(user));
    }
}
