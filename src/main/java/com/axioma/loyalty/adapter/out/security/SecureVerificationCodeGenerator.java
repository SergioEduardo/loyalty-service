package com.axioma.loyalty.adapter.out.security;

import com.axioma.loyalty.domain.port.out.VerificationCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 6 dígitos uniformes (000000–999999) de SecureRandom. */
@Component
public class SecureVerificationCodeGenerator implements VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }
}
