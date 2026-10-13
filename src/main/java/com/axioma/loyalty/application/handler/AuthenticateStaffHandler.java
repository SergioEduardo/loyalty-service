package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.query.AuthenticateStaffQuery;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.AccessTokenDecoder;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Verifica el access token y que su sesión siga vigente, para que desactivar una cuenta o
 * cambiar su contraseña invalide sus tokens de inmediato. El rol es el actual de la cuenta.
 */
@Service
@RequiredArgsConstructor
public class AuthenticateStaffHandler implements Handler<AuthenticateStaffQuery, Mono<StaffPrincipal>> {

    private final AccessTokenDecoder accessTokenDecoder;
    private final SessionRepository sessionRepository;

    @Override
    public Mono<StaffPrincipal> handle(AuthenticateStaffQuery query) {
        if (query.accessToken() == null || query.accessToken().isBlank()) {
            return Mono.error(new LoyaltyException(ErrorCode.UNAUTHORIZED));
        }
        return accessTokenDecoder.decode(query.accessToken())
                .flatMap(claims -> sessionRepository
                        .findActiveRole(claims.sessionId(), claims.tenantId(), claims.userId())
                        .map(role -> new StaffPrincipal(claims.userId(), claims.tenantId(), role, claims.sessionId())))
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.UNAUTHORIZED)));
    }
}
