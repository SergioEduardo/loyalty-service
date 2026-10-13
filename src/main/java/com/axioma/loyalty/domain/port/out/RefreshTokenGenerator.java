package com.axioma.loyalty.domain.port.out;

/** Tokens de renovación opacos. En la base solo se guarda su hash. */
public interface RefreshTokenGenerator {

    String generate();

    /** SHA-256 en hexadecimal (64 caracteres). */
    String hash(String refreshToken);
}
