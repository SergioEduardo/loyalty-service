package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.config.SecurityProperties;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Largo mínimo configurable y máximo de 72 bytes (bcrypt ignora lo que sigue). */
@Component
public class PasswordPolicy {

    public static final int MAX_BYTES = 72;

    private final int minLength;

    public PasswordPolicy(SecurityProperties properties) {
        this.minLength = properties.passwordMinLength();
    }

    public void check(String password) {
        if (password == null
                || password.codePointCount(0, password.length()) < minLength
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new LoyaltyException(ErrorCode.WEAK_PASSWORD);
        }
    }
}
