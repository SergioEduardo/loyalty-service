package com.axioma.loyalty.domain.model.error;

/**
 * El proveedor no entregó el código. RECIPIENT_UNREACHABLE: el número no puede recibir por ese canal
 * (400, la persona puede probar otro); VERIFICATION_UNAVAILABLE: cualquier otro fallo (503).
 * providerCode es el código numérico de error del proveedor (o null) para correlacionar en los logs;
 * nunca llega a la respuesta.
 */
public class VerificationDeliveryException extends LoyaltyException {

    private final String providerCode;

    private VerificationDeliveryException(ErrorCode code, String providerCode) {
        super(code);
        this.providerCode = providerCode;
    }

    public static VerificationDeliveryException recipientUnreachable(String providerCode) {
        return new VerificationDeliveryException(ErrorCode.RECIPIENT_UNREACHABLE, providerCode);
    }

    public static VerificationDeliveryException unavailable(String providerCode) {
        return new VerificationDeliveryException(ErrorCode.VERIFICATION_UNAVAILABLE, providerCode);
    }

    public String getProviderCode() {
        return providerCode;
    }
}
