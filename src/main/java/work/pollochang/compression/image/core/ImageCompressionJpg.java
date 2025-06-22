package work.pollochang.compression.image.core;

import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.learn.LearnedParams;
import work.pollochang.compression.image.learn.jpg.SimilarityKey;
import work.pollochang.compression.image.report.CompressionParams;
import work.pollochang.compression.image.tools.FileTools;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static work.pollochang.compression.image.learn.Learn.createKey;
import static work.pollochang.compression.image.tools.ImageTools.resizeImage;

@Slf4j
public class ImageCompressionJpg {

    public static boolean compressJpgWithTargetSize(BufferedImage originalImage, long originalSize, Path outputFile, CompressionParams params, Map<SimilarityKey, LearnedParams> cache) throws IOException {
        // 1. 產生快取 Key
        SimilarityKey key = createKey(originalImage, originalSize);
        LearnedParams cachedParams = cache.get(key);

        // 2. 如果快取命中，嘗試使用已學習的參數
        if (cachedParams != null) {
            log.debug("快取命中: {} -> 使用學習參數 (q={}, s={})", outputFile.getFileName(), cachedParams.quality(), cachedParams.scale());
            BufferedImage imageToCompress = originalImage;
            boolean isResized = false;
            // 如果學習到的參數包含縮放，則先縮放
            if (cachedParams.scale() < 1.0) {
                imageToCompress = resizeImage(originalImage, cachedParams.scale());
                isResized = true;
            }

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                compressJpgToStream(imageToCompress, bos, cachedParams.quality());
                if (bos.size() <= params.targetMaxSizeBytes()) {
                    Files.write(outputFile, bos.toByteArray());
                    log.info("快取成功: {} 使用學習參數直接達成目標。", outputFile.getFileName());
                    if (isResized) imageToCompress.flush();
                    return true;
                } else {
                    log.warn("快取失效: {} 使用學習參數後檔案大小 ({}) 仍超標，退回標準壓縮流程。",
                            outputFile.getFileName(), FileTools.formatFileSize(bos.size()));
                    if (isResized) imageToCompress.flush();
                }
            }
        }

        final float QUALITY_STEP = 0.1f;
        final double SCALE_STEP = 0.85;

        BufferedImage currentImage = originalImage;
        boolean isOriginal = true;
        double currentScale = 1.0;

        try {
            // 迴圈縮放
            for (double scale = 1.0; scale > 0.1; scale = (scale == 1.0) ? SCALE_STEP : scale * SCALE_STEP) {
                if (scale < 1.0) {
                    if (!isOriginal) currentImage.flush();
                    currentImage = resizeImage(originalImage, scale);
                    isOriginal = false;
                    currentScale = scale;
                    log.debug("檔案仍然過大，縮放至 {}%", (int) (scale * 100));
                }

                // 迴圈品質
                for (float quality = params.quality(); quality >= 0.1f; quality -= QUALITY_STEP) {
                    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        compressJpgToStream(currentImage, bos, quality);
                        if (bos.size() <= params.targetMaxSizeBytes()) {
                            log.debug("找到合適參數: quality={}, scale={}", String.format("%.2f", quality), String.format("%.2f", currentScale));
                            Files.write(outputFile, bos.toByteArray());

                            // *** 學習功能核心 ***
                            LearnedParams newParams = new LearnedParams(quality, currentScale);
                            cache.put(key, newParams);
                            log.info("{} - 學習並儲存新參數 -> (q={}, s={})", outputFile, newParams.quality(), newParams.scale());

                            return true;
                        }
                    }
                }
            }
        } finally {
            if (!isOriginal && currentImage != null) {
                currentImage.flush();
            }
        }

        log.warn("無法在目標大小限制下完成壓縮: {}", outputFile.getFileName());
        return false;
    }

    private static void compressJpgToStream(BufferedImage image, OutputStream os, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
