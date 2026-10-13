package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.UploadTenantLogoCommand;
import com.axioma.loyalty.application.validator.UploadTenantLogoValidator;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.TenantProfile;
import com.axioma.loyalty.domain.port.out.ImageNormalizerPort;
import com.axioma.loyalty.domain.port.out.LogoStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Normaliza el logo, lo sube con un nombre que depende de su contenido, apunta el negocio a él
 * (con su TenantChanged) y al final borra el anterior. La subida va fuera de la transacción para
 * no retener la fila bloqueada mientras se habla con Cloud Storage.
 */
@Service
@RequiredArgsConstructor
public class UploadTenantLogoHandler implements Handler<UploadTenantLogoCommand, Mono<TenantProfile>> {

    private final UploadTenantLogoValidator validator;
    private final ImageNormalizerPort imageNormalizer;
    private final LogoStoragePort logoStorage;
    private final TenantChangeWriter tenantChangeWriter;
    private final LogoCleanup logoCleanup;

    @Override
    public Mono<TenantProfile> handle(UploadTenantLogoCommand command) {
        return validator.validate(command)
                .flatMap(format -> imageNormalizer.normalize(command.content(), format))
                .flatMap(png -> logoStorage.store(TenantLogo.objectKey(command.tenantId(), png), png))
                .flatMap(logo -> tenantChangeWriter.apply(command.tenantId(), tenant -> tenant.withLogo(logo)))
                .flatMap(update -> logoCleanup.deleteReplaced(update).thenReturn(TenantProfile.from(update.after())));
    }
}
