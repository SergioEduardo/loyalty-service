package com.axioma.loyalty.adapter.out.security;

import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.VerificationCodeHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * HMAC-SHA256 en hexadecimal (64 caracteres) con llave OTP_HASH_SALT sobre "{tenantId}|{phoneE164}|{code}".
 * Ligar negocio y teléfono impide reutilizar un hash en otra verificación. Sin sal válida, el alta
 * responde VERIFICATION_UNAVAILABLE.
 */
@Slf4j
@Component
public class HmacVerificationCodeHasher implements VerificationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacVerificationCodeHasher(EnrollProperties properties) {
        if (properties.saltConfigured()) {
            key = new SecretKeySpec(properties.otpHashSalt().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        } else {
            key = null;
            log.warn("OTP_HASH_SALT vacía o de menos de {} bytes: el alta de tarjetas responde VERIFICATION_UNAVAILABLE",
                    EnrollProperties.MIN_SALT_BYTES);
        }
    }

    @Override
    public Mono<Void> ensureReady() {
        return key == null ? Mono.error(new LoyaltyException(ErrorCode.VERIFICATION_UNAVAILABLE)) : Mono.empty();
    }

    @Override
    public String hash(UUID tenantId, PhoneE164 phone, String code) {
        return HexFormat.of().formatHex(mac(tenantId, phone, code));
    }

    @Override
    public boolean matches(String expectedHash, UUID tenantId, PhoneE164 phone, String code) {
        if (expectedHash == null) {
            return false;
        }
        byte[] actual = hash(tenantId, phone, code).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expectedHash.strip().getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] mac(UUID tenantId, PhoneE164 phone, String code) {
        if (key == null) {
            throw new LoyaltyException(ErrorCode.VERIFICATION_UNAVAILABLE);
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal((tenantId + "|" + phone.value() + "|" + code).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }
}
