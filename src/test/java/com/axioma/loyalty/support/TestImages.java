package com.axioma.loyalty.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;

/** Imágenes de prueba generadas en memoria (WebP sale de src/test/resources/images: el JDK no lo escribe). */
public final class TestImages {

    public static final Color CENTER = Color.BLUE;
    public static final Color SIDES = Color.RED;

    private TestImages() {
    }

    /** Ancho > alto: bandas laterales rojas y el cuadrado central azul. */
    public static BufferedImage banded(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(SIDES);
        g.fillRect(0, 0, width, height);
        g.setColor(CENTER);
        g.fillRect((width - height) / 2, 0, height, height);
        g.dispose();
        return image;
    }

    public static byte[] png(int width, int height) {
        return encode(banded(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(banded(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    /** 300 × 200, mismas bandas; generado con cwebp. */
    public static byte[] webp(boolean lossless) {
        return resource("/images/banded-300x200-" + (lossless ? "lossless" : "lossy") + ".webp");
    }

    public static byte[] svg() {
        return """
                <?xml version="1.0"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><script>alert(1)</script></svg>
                """.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * PNG válido que declara width × height en blanco y negro (1 bit): pesa pocos KB comprimido,
     * pero decodificado ocuparía width × height × 4 bytes.
     */
    public static byte[] pngBomb(int width, int height) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 1).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).array());
        var idat = new ByteArrayOutputStream();
        try (var deflate = new DeflaterOutputStream(idat, new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] row = new byte[1 + (width + 7) / 8];
            for (int y = 0; y < height; y++) {
                deflate.write(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        chunk(out, "IDAT", idat.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** Ancho y alto de un PNG, leyendo su cabecera IHDR. */
    public static int[] pngSize(byte[] png) {
        ByteBuffer buffer = ByteBuffer.wrap(png);
        return new int[] {buffer.getInt(16), buffer.getInt(20)};
    }

    /** Tipos de chunk de un PNG, en orden. */
    public static java.util.List<String> pngChunks(byte[] png) {
        var chunks = new java.util.ArrayList<String>();
        ByteBuffer buffer = ByteBuffer.wrap(png);
        int offset = 8;
        while (offset < png.length) {
            int length = buffer.getInt(offset);
            chunks.add(new String(png, offset + 4, 4, StandardCharsets.US_ASCII));
            offset += 12 + length;
        }
        return chunks;
    }

    public static BufferedImage decode(byte[] content) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static byte[] encode(BufferedImage image, String format) {
        var out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("Sin writer para " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] resource(String path) {
        try (InputStream in = TestImages.class.getResourceAsStream(path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
