package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.ImageFormat;
import reactor.core.publisher.Mono;

public interface ImageNormalizerPort {

    /**
     * Recorte cuadrado centrado, escalado a TenantLogo.SIZE_PX y re-codificado a PNG sin metadatos.
     * Falla con IMAGE_DIMENSIONS_TOO_LARGE antes de decodificar si la imagen supera
     * TenantLogo.MAX_DIMENSION, o con INVALID_IMAGE si no se puede leer.
     */
    Mono<byte[]> normalize(byte[] content, ImageFormat format);
}
