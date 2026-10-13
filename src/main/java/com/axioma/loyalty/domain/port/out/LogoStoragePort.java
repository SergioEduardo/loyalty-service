package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.StoredLogo;
import reactor.core.publisher.Mono;

public interface LogoStoragePort {

    /** Guarda el PNG normalizado como objeto público. Falla con STORAGE_UNAVAILABLE. */
    Mono<StoredLogo> store(String objectKey, byte[] png);

    /** Borra el objeto; si ya no existe, no falla. */
    Mono<Void> delete(String objectKey);
}
