package com.axioma.loyalty.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.support.TestImages;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageFormatTest {

    @Test
    void detectsByMagicBytes() {
        assertThat(ImageFormat.detect(TestImages.png(10, 10))).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(TestImages.jpeg(10, 10))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(TestImages.webp(true))).contains(ImageFormat.WEBP);
        assertThat(ImageFormat.detect(TestImages.webp(false))).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageFormat.detect(TestImages.svg())).isEmpty();
        assertThat(ImageFormat.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("hola, no soy un png".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[] {(byte) 0x89, 'P', 'N'})).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(null)).isEmpty();
    }

    @Test
    void objectKeyDependsOnContent() {
        var tenantId = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        String key = TenantLogo.objectKey(tenantId, new byte[] {1, 2, 3});
        assertThat(key).matches("tenants/11111111-1111-1111-1111-111111111111/logo-[0-9a-f]{64}\\.png");
        assertThat(TenantLogo.objectKey(tenantId, new byte[] {1, 2, 3})).isEqualTo(key);
        assertThat(TenantLogo.objectKey(tenantId, new byte[] {1, 2, 4})).isNotEqualTo(key);
    }
}
