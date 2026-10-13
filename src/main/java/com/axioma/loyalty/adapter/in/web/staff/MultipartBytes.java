package com.axioma.loyalty.adapter.in.web.staff;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Lee un archivo subido a memoria, cortando en cuanto pasa del máximo. Si la parte ya rebasó el
 * límite del lector multipart (spring.webflux.multipart.*), el error llega al resolver la parte.
 */
final class MultipartBytes {

    private MultipartBytes() {
    }

    static Mono<byte[]> read(Mono<FilePart> file, int maxBytes) {
        return file.flatMap(part -> DataBufferUtils.join(part.content(), maxBytes))
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(new byte[0])
                .onErrorMap(DataBufferLimitException.class, e -> new LoyaltyException(ErrorCode.LOGO_TOO_LARGE, e));
    }
}
