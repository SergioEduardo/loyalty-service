package com.axioma.loyalty.domain.model.error;

/** Errores de negocio con su mensaje para la persona usuaria. */
public enum ErrorCode {
    INVALID_SLUG("La dirección del negocio no es válida."),
    TENANT_NOT_FOUND("No se encontró el negocio."),
    SLUG_ALREADY_EXISTS("Ya existe un negocio con esa dirección."),
    INVALID_TEXT("Algún texto está vacío o es demasiado largo."),
    INVALID_BRAND_COLOR("El color debe tener el formato #RRGGBB."),
    INVALID_SETTINGS("La configuración del programa no es válida."),
    INVALID_USER("Los datos de la cuenta no son válidos."),
    WEAK_PASSWORD("La contraseña no cumple los requisitos."),
    USERNAME_TAKEN("Ese usuario ya existe en el negocio."),
    USER_NOT_FOUND("No se encontró la cuenta."),
    UNAUTHORIZED("Se requiere autenticación."),
    FORBIDDEN("No tienes permiso para esta operación."),
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
