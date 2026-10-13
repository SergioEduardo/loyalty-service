package com.axioma.loyalty.domain.port.out;

/** Código de verificación de 6 dígitos. */
public interface VerificationCodeGenerator {

    String generate();
}
