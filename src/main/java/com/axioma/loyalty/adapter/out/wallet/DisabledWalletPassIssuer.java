package com.axioma.loyalty.adapter.out.wallet;

import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.IssuedPass;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import reactor.core.publisher.Mono;

/** Sin WALLET_SERVICE_URL: la tarjeta se crea, pero el pase no; responde PASS_SERVICE_UNAVAILABLE. */
public class DisabledWalletPassIssuer implements WalletPassIssuerPort {

    @Override
    public Mono<IssuedPass> issue(Card card) {
        return Mono.error(new LoyaltyException(ErrorCode.PASS_SERVICE_UNAVAILABLE));
    }
}
