package com.axioma.loyalty.adapter.out.image;

import static com.axioma.loyalty.application.ErrorAssertions.hasCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axioma.loyalty.domain.model.ImageFormat;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.support.TestImages;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class ImageIoImageNormalizerTest {

    private final ImageIoImageNormalizer normalizer = new ImageIoImageNormalizer(Schedulers.immediate());

    @Test
    void pngBecomesCenteredSquare512Png() {
        assertNormalizedCenter(ImageIoImageNormalizer.process(TestImages.png(900, 600), ImageFormat.PNG));
    }

    @Test
    void jpegBecomesCenteredSquare512Png() {
        assertNormalizedCenter(ImageIoImageNormalizer.process(TestImages.jpeg(300, 200), ImageFormat.JPEG));
    }

    @Test
    void webpLosslessAndLossyBecomeCenteredSquare512Png() {
        assertNormalizedCenter(ImageIoImageNormalizer.process(TestImages.webp(true), ImageFormat.WEBP));
        assertNormalizedCenter(ImageIoImageNormalizer.process(TestImages.webp(false), ImageFormat.WEBP));
    }

    @Test
    void largeImageWithinLimitIsSubsampledAndScaled() {
        assertNormalizedCenter(ImageIoImageNormalizer.process(TestImages.png(4000, 3000), ImageFormat.PNG));
    }

    @Test
    void smallImageIsScaledUp() {
        assertNormalizedCenter(ImageIoImageNormalizer.process(TestImages.png(60, 40), ImageFormat.PNG));
    }

    @Test
    void outputHasNoMetadataChunks() {
        byte[] png = ImageIoImageNormalizer.process(TestImages.jpeg(300, 200), ImageFormat.JPEG);
        assertThat(TestImages.pngChunks(png)).allMatch(type -> type.equals("IHDR") || type.equals("IDAT")
                || type.equals("IEND"));
    }

    @Test
    void sameInputGivesSameBytes() {
        byte[] source = TestImages.png(300, 200);
        assertThat(ImageIoImageNormalizer.process(source, ImageFormat.PNG))
                .isEqualTo(ImageIoImageNormalizer.process(source, ImageFormat.PNG));
    }

    @Test
    void decompressionBombIsRejectedBeforeDecoding() {
        byte[] bomb = TestImages.pngBomb(20_000, 20_000);
        assertThat(bomb.length).isLessThan(200_000); // decodificada serían 1.6 GB en ARGB

        StepVerifier.create(normalizer.normalize(bomb, ImageFormat.PNG))
                .expectErrorSatisfies(hasCode(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE))
                .verify();
    }

    @Test
    void justOverTheLimitIsRejected() {
        assertThatThrownBy(() -> ImageIoImageNormalizer.process(TestImages.pngBomb(4001, 10), ImageFormat.PNG))
                .satisfies(hasCode(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE));
    }

    @Test
    void truncatedOrCorruptImageIsInvalid() {
        byte[] png = TestImages.png(300, 200);
        assertThatThrownBy(() -> ImageIoImageNormalizer.process(Arrays.copyOf(png, 40), ImageFormat.PNG))
                .satisfies(hasCode(ErrorCode.INVALID_IMAGE));
        byte[] jpegHeaderOnly = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertThatThrownBy(() -> ImageIoImageNormalizer.process(jpegHeaderOnly, ImageFormat.JPEG))
                .satisfies(hasCode(ErrorCode.INVALID_IMAGE));
    }

    private static void assertNormalizedCenter(byte[] png) {
        assertThat(ImageFormat.detect(png)).contains(ImageFormat.PNG);
        assertThat(TestImages.pngSize(png)).containsExactly(512, 512);
        BufferedImage image = TestImages.decode(png);
        // Solo queda el cuadrado central (azul); las bandas rojas se recortaron, incluso en los bordes.
        for (int[] point : new int[][] {{256, 256}, {2, 2}, {509, 256}, {256, 509}}) {
            assertThat(isBlue(new Color(image.getRGB(point[0], point[1]), true)))
                    .as("pixel %s,%s", point[0], point[1]).isTrue();
        }
    }

    private static boolean isBlue(Color color) {
        return color.getBlue() > 200 && color.getRed() < 60 && color.getAlpha() == 255;
    }
}
