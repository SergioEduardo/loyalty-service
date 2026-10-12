package com.axioma.loyalty.domain.model.error;

/** Errores de negocio con su mensaje para la persona usuaria. */
public enum ErrorCode {
    INVALID_SLUG("La dirección del negocio no es válida."),
    TENANT_NOT_FOUND("No se encontró el negocio."),
    INVALID_REQUEST("La solicitud no es válida."),
    INTERNAL_ERROR("Ocurrió un error. Intenta de nuevo.");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
