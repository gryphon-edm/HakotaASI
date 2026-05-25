/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.internal;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * High-performance, lightweight utilities for extracting image metadata.
 * <p>
 * This class uses a header-only image stream reader to parse dimensions without performing
 * a heavy, memory-intensive pixel decode of the entire image array.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImageMetadataUtils {

    /**
     * Immutable container for parsed image dimensions and metadata.
     */
    @Value
    public static class ImageMetadata {
        /**
         * The parsed width of the image in pixels.
         */
        int width;
        
        /**
         * The parsed height of the image in pixels.
         */
        int height;
        
        /**
         * The MIME type of the parsed image (e.g. "image/png").
         */
        String mimeType;
    }

    /**
     * Reads the dimensions and metadata of an image from its raw byte array.
     * <p>
     * Utilizes standard JDK ImageReader SPI to parse metadata from headers. This is
     * extremely fast (microsecond latency) and consumes virtually zero CPU compared to
     * full image decoding.
     * </p>
     * 
     * @param data The raw image file bytes.
     * @return The parsed {@link ImageMetadata}, or null if reading fails or format is unsupported.
     */
    public static ImageMetadata readMetadata(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    String format = reader.getFormatName().toLowerCase();
                    return new ImageMetadata(width, height, "image/" + format);
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse image dimensions from headers, returning null", e);
        }
        return null;
    }

    /**
     * Calculates the token consumption of an image under OpenAI's vision billing scheme.
     * <p>
     * Implements the official OpenAI High-Detail scaling and tiling algorithm:
     * 1. Proportional downscale to fit within a 2048x2048 box.
     * 2. Proportional downscale such that the shortest side is exactly 768px.
     * 3. Count of 512x512 pixel tiles needed to cover the scaled image.
     * 4. Billing of 170 tokens per tile plus a flat 85 tokens base cost.
     * </p>
     * @param metadata The parsed image dimensions and metadata.
     * @return The calculated high-detail token count, or 85 as a low-detail fallback.
     */
    public static int calculateOpenAiTileTokens(ImageMetadata metadata) {
        if (metadata == null) {
                    return 85; // Low-detail flat-rate fallback
                }
                int width = metadata.getWidth();
                int height = metadata.getHeight();

                // OpenAI Vision High-Detail Scaling and Tiling Algorithm:
                // 1. Proportional scale to fit within a 2048x2048 box
                if (width > 2048 || height > 2048) {
                    double maxRatio = 2048.0 / Math.max(width, height);
                    width = (int) (width * maxRatio);
                    height = (int) (height * maxRatio);
                }

                // 2. Proportional scale shortest side to 768px
                double minRatio = 768.0 / Math.min(width, height);
                width = (int) (width * minRatio);
                height = (int) (height * minRatio);

                // 3. Count 512x512 pixel tiles
                int tilesW = (int) Math.ceil(width / 512.0);
                int tilesH = (int) Math.ceil(height / 512.0);

                // 4. Calculate final billing (170 tokens per tile + 85 tokens flat base cost)
                return (tilesW * tilesH) * 170 + 85;
    }

}
