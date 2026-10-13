package com.axioma.loyalty.support;

import com.axioma.loyalty.domain.model.StoredLogo;
import com.axioma.loyalty.domain.port.out.LogoStoragePort;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Mono;

/** Fake de LogoStoragePort: guarda los objetos en memoria y anota los borrados. */
public class InMemoryLogoStorage implements LogoStoragePort {

    public static final String BASE_URL = "https://storage.googleapis.com/test-logos";

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final List<String> deleted = new CopyOnWriteArrayList<>();
    private volatile boolean failDeletes;

    @Override
    public Mono<StoredLogo> store(String objectKey, byte[] png) {
        return Mono.fromCallable(() -> {
            objects.put(objectKey, png);
            return new StoredLogo(objectKey, BASE_URL + "/" + objectKey);
        });
    }

    @Override
    public Mono<Void> delete(String objectKey) {
        if (failDeletes) {
            return Mono.error(new IllegalStateException("borrado simulado fallido"));
        }
        return Mono.fromRunnable(() -> {
            objects.remove(objectKey);
            deleted.add(objectKey);
        });
    }

    public Map<String, byte[]> objects() {
        return objects;
    }

    public List<String> deleted() {
        return deleted;
    }

    public void failDeletes(boolean fail) {
        this.failDeletes = fail;
    }

    public void clear() {
        objects.clear();
        deleted.clear();
        failDeletes = false;
    }
}
