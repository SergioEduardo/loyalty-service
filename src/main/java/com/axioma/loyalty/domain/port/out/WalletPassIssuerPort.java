package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.IssuedPass;
import reactor.core.publisher.Mono;

/**
 * IssuePass del wallet-service (12.8). Idempotente por tarjeta. Errores: TEMPLATE_NOT_READY o
 * PASS_SERVICE_UNAVAILABLE, que dependen solo de Apple: sin enlace de Google el pase se emite igual
 * con googleUrl en null.
 */
public interface WalletPassIssuerPort {

    Mono<IssuedPass> issue(Card card);
}
