package com.axioma.loyalty.adapter.out.security;

import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import org.springframework.stereotype.Component;

/** 256 bits de SecureRandom en base64url; en la base solo va su SHA-256. */
@Component
public class SecureRefreshTokenGenerator implements RefreshTokenGenerator {

    @Override
    public String generate() {
        return OpaqueTokens.generate();
    }

    @Override
    public String hash(String refreshToken) {
        return OpaqueTokens.sha256Hex(refreshToken);
    }
}
