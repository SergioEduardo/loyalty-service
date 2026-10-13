package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.UploadTenantLogoCommand;
import com.axioma.loyalty.domain.model.ImageFormat;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Peso y formato (por sus primeros bytes). Las dimensiones las revisa el normalizador. */
@Component
public class UploadTenantLogoValidator {

    public Mono<ImageFormat> validate(UploadTenantLogoCommand command) {
        return Mono.fromCallable(() -> check(command));
    }

    private static ImageFormat check(UploadTenantLogoCommand command) {
        byte[] content = command.content();
        if (content == null || content.length == 0) {
            throw new LoyaltyException(ErrorCode.INVALID_IMAGE);
        }
        if (content.length > TenantLogo.MAX_BYTES) {
            throw new LoyaltyException(ErrorCode.LOGO_TOO_LARGE);
        }
        return ImageFormat.detect(content)
                .orElseThrow(() -> new LoyaltyException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT));
    }
}
