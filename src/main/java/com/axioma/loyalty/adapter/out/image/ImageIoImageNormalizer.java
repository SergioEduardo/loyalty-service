package com.axioma.loyalty.adapter.out.image;

import com.axioma.loyalty.domain.model.ImageFormat;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.ImageNormalizerPort;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Normaliza logos con ImageIO (PNG y JPEG del JDK, WebP de TwelveMonkeys).
 * <ul>
 *   <li>Usa solo el lector del formato detectado por magic bytes; nunca deja que ImageIO adivine.</li>
 *   <li>Lee ancho y alto de la cabecera y rechaza imágenes de más de MAX_DIMENSION por lado antes de
 *       decodificar: un PNG de pocos KB puede declarar 20000 × 20000 píxeles (imagen bomba).</li>
 *   <li>Decodifica solo el cuadrado central y, si es grande, submuestreado a no menos de 2× el lado final:
 *       la memoria queda acotada (≈16 MB en el peor caso) aunque la imagen mida 4000 × 4000.</li>
 *   <li>Re-dibuja en ARGB y escribe un PNG nuevo: no pasa ningún metadato (EXIF, ICC, texto).</li>
 * </ul>
 * Corre en imageProcessingScheduler (pocos hilos) para no bloquear el event loop ni procesar
 * muchas imágenes a la vez.
 */
@Slf4j
@Component
public class ImageIoImageNormalizer implements ImageNormalizerPort {

    private final Scheduler scheduler;

    public ImageIoImageNormalizer(@Qualifier("imageProcessingScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
        // Registra los plugins (WebP) con el classloader de la aplicación, no el del primer hilo que use ImageIO.
        ImageIO.scanForPlugins();
        if (!ImageIO.getImageReadersByFormatName(formatName(ImageFormat.WEBP)).hasNext()) {
            log.error("No hay lector WebP para ImageIO: los logos WebP fallarán");
        }
    }

    @Override
    public Mono<byte[]> normalize(byte[] content, ImageFormat format) {
        return Mono.fromCallable(() -> process(content, format)).subscribeOn(scheduler);
    }

    static byte[] process(byte[] content, ImageFormat format) {
        try {
            return encodePng(resize(decodeCenteredSquare(content, format), TenantLogo.SIZE_PX));
        } catch (LoyaltyException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Archivo corrupto o truncado, variante no soportada (p. ej. JPEG CMYK), etc.
            throw new LoyaltyException(ErrorCode.INVALID_IMAGE, e);
        }
    }

    private static BufferedImage decodeCenteredSquare(byte[] content, ImageFormat format) throws IOException {
        ImageReader reader = readerFor(format);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0) {
                throw new LoyaltyException(ErrorCode.INVALID_IMAGE);
            }
            if (width > TenantLogo.MAX_DIMENSION || height > TenantLogo.MAX_DIMENSION) {
                throw new LoyaltyException(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
            }
            int side = Math.min(width, height);
            int step = Math.max(1, side / (TenantLogo.SIZE_PX * 2));
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceRegion(new Rectangle((width - side) / 2, (height - side) / 2, side, side));
            param.setSourceSubsampling(step, step, 0, 0);
            return reader.read(0, param);
        } finally {
            reader.dispose();
        }
    }

    private static ImageReader readerFor(ImageFormat format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName(format));
        if (!readers.hasNext()) {
            throw new LoyaltyException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
        }
        return readers.next();
    }

    private static String formatName(ImageFormat format) {
        return switch (format) {
            case PNG -> "png";
            case JPEG -> "jpeg";
            case WEBP -> "webp";
        };
    }

    /** Escala a target × target. Al reducir mucho, lo hace por mitades para no perder calidad. */
    static BufferedImage resize(BufferedImage source, int target) {
        BufferedImage current = source;
        int size = Math.min(source.getWidth(), source.getHeight());
        while (size / 2 >= target) {
            size /= 2;
            current = draw(current, size);
        }
        // Siempre se dibuja al menos una vez: deja la imagen en ARGB sin nada del original.
        return draw(current, target);
    }

    private static BufferedImage draw(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        var bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), null);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
