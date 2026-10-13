package com.axioma.loyalty.adapter.out.storage;

import static com.axioma.loyalty.application.ErrorAssertions.hasCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.LogoStorageProperties;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

/** GcsLogoStorage contra fake-gcs-server (emulador de la API JSON de Cloud Storage). Se omite sin Docker. */
@Testcontainers(disabledWithoutDocker = true)
class GcsLogoStorageIT {

    private static final String BUCKET = "test-logos";
    private static final String KEY = "tenants/11111111-1111-1111-1111-111111111111/logo-abc.png";

    @Container
    static GenericContainer<?> gcs = new GenericContainer<>("fsouza/fake-gcs-server:1.56.1")
            .withCommand("-scheme", "http", "-backend", "memory")
            .withExposedPorts(4443);

    static Storage client;

    @BeforeAll
    static void bucket() {
        client = StorageOptions.newBuilder()
                .setHost("http://" + gcs.getHost() + ":" + gcs.getMappedPort(4443))
                .setProjectId("test")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
        client.create(BucketInfo.of(BUCKET));
    }

    @Test
    void storesPublicImmutablePngAndDeletesIdempotently() {
        var storage = new GcsLogoStorage(new LogoStorageProperties(BUCKET, null), client);
        byte[] png = {1, 2, 3};

        StepVerifier.create(storage.store(KEY, png))
                .assertNext(logo -> {
                    assertThat(logo.objectKey()).isEqualTo(KEY);
                    assertThat(logo.url()).isEqualTo("https://storage.googleapis.com/" + BUCKET + "/" + KEY);
                })
                .verifyComplete();
        var blob = client.get(BlobId.of(BUCKET, KEY));
        assertThat(blob.getContent()).isEqualTo(png);
        assertThat(blob.getContentType()).isEqualTo("image/png");
        assertThat(blob.getCacheControl()).isEqualTo("public, max-age=31536000, immutable");

        StepVerifier.create(storage.delete(KEY)).verifyComplete();
        assertThat(client.get(BlobId.of(BUCKET, KEY))).isNull();
        StepVerifier.create(storage.delete(KEY)).verifyComplete();
    }

    @Test
    void customPublicBaseUrl() {
        var storage = new GcsLogoStorage(new LogoStorageProperties(BUCKET, "https://logos.example.com/"), client);
        StepVerifier.create(storage.store("tenants/x/logo-1.png", new byte[] {9}))
                .assertNext(logo -> assertThat(logo.url()).isEqualTo("https://logos.example.com/tenants/x/logo-1.png"))
                .verifyComplete();
    }

    @Test
    void withoutBucketUploadIsUnavailable() {
        var storage = new GcsLogoStorage(new LogoStorageProperties("", null));
        StepVerifier.create(storage.store(KEY, new byte[] {1}))
                .expectErrorSatisfies(hasCode(ErrorCode.STORAGE_UNAVAILABLE))
                .verify();
    }

    @Test
    void storageErrorsBecomeUnavailable() {
        var storage = new GcsLogoStorage(new LogoStorageProperties("bucket-que-no-existe", null), client);
        StepVerifier.create(storage.store(KEY, new byte[] {1}))
                .expectErrorSatisfies(hasCode(ErrorCode.STORAGE_UNAVAILABLE))
                .verify();
    }
}
