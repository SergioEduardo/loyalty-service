package com.axioma.loyalty.adapter.out.storage;

import com.axioma.loyalty.config.LogoStorageProperties;
import com.axioma.loyalty.domain.model.StoredLogo;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.LogoStoragePort;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Logos en Cloud Storage. Credenciales por ADC (en Cloud Run, la cuenta de servicio del servicio;
 * sin llaves). El cliente se crea al primer uso, así el servicio arranca sin credenciales en local.
 * Sin bucket configurado, subir responde STORAGE_UNAVAILABLE. El cliente es bloqueante: corre en
 * boundedElastic.
 */
@Slf4j
@Component
public class GcsLogoStorage implements LogoStoragePort {

    private final LogoStorageProperties properties;
    private volatile Storage storage;

    @Autowired
    public GcsLogoStorage(LogoStorageProperties properties) {
        this(properties, null);
    }

    GcsLogoStorage(LogoStorageProperties properties, Storage storage) {
        this.properties = properties;
        this.storage = storage;
    }

    @Override
    public Mono<StoredLogo> store(String objectKey, byte[] png) {
        return Mono.fromCallable(() -> {
                    BlobInfo blob = BlobInfo.newBuilder(BlobId.of(properties.bucket(), objectKey))
                            .setContentType(TenantLogo.CONTENT_TYPE)
                            .setCacheControl(TenantLogo.CACHE_CONTROL)
                            .build();
                    client().create(blob, png);
                    return new StoredLogo(objectKey, properties.publicUrl(objectKey));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> !(e instanceof LoyaltyException), e -> {
                    log.error("No se pudo subir el logo objectKey={}", objectKey, e);
                    return new LoyaltyException(ErrorCode.STORAGE_UNAVAILABLE, e);
                });
    }

    @Override
    public Mono<Void> delete(String objectKey) {
        return Mono.fromCallable(() -> client().delete(BlobId.of(properties.bucket(), objectKey)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Storage client() {
        if (!properties.isConfigured()) {
            throw new LoyaltyException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        Storage current = storage;
        if (current == null) {
            synchronized (this) {
                if (storage == null) {
                    storage = StorageOptions.getDefaultInstance().getService();
                }
                current = storage;
            }
        }
        return current;
    }
}
