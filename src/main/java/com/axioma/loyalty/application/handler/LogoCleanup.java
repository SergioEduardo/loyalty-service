package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.TenantUpdate;
import com.axioma.loyalty.domain.port.out.LogoStoragePort;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Borra el logo anterior una vez confirmado el cambio en la base. Si el borrado falla, el cambio
 * ya está hecho: solo queda un objeto huérfano en el bucket, así que no se propaga el error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogoCleanup {

    private final LogoStoragePort logoStorage;

    public Mono<Void> deleteReplaced(TenantUpdate update) {
        String previous = update.before().logoObjectKey();
        if (previous == null || Objects.equals(previous, update.after().logoObjectKey())) {
            return Mono.empty();
        }
        return logoStorage.delete(previous)
                .onErrorResume(e -> {
                    log.warn("No se pudo borrar el logo anterior tenantId={} objectKey={}",
                            update.after().id(), previous, e);
                    return Mono.empty();
                });
    }
}
