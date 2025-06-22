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

    // in work.pollochang.compression.image.core.ImageCompressionJpg.java

    public static boolean compressJpgWithTargetSize(BufferedImage originalImage, long originalSize, Path outputFile, CompressionParams params, Map<SimilarityKey, LearnedParams> cache) throws IOException {
        // 1. 產生快取 Key
        SimilarityKey key = createKey(originalImage, originalSize);
        LearnedParams cachedParams = cache.get(key);

        // 2. 如果快取命中，嘗試使用已學習的參數 (這部分不變)
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

        // 不再需要固定的品質和縮放級距
        // final float QUALITY_STEP = 0.1f;
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

                // ==================== 【修改核心】 ====================
                // 刪除舊的 for-loop，改為呼叫二分搜尋法
                float bestQuality = findBestQualityByBinarySearch(currentImage, params.targetMaxSizeBytes(), params.quality());

                // 如果找到了合適的品質 (bestQuality > 0)
                if (bestQuality > 0) {
                    log.debug("找到合適參數: quality={}, scale={}", String.format("%.2f", bestQuality), String.format("%.2f", currentScale));

                    // 使用找到的最佳品質進行最終壓縮並寫入檔案
                    try (ByteArrayOutputStream bos = new ByteArrayOutputStream((int)params.targetMaxSizeBytes())) {
                        compressJpgToStream(currentImage, bos, bestQuality);
                        Files.write(outputFile, bos.toByteArray());
                    }

                    // *** 學習功能核心 ***
                    LearnedParams newParams = new LearnedParams(bestQuality, currentScale);
                    cache.put(key, newParams);
                    log.info("{} - 學習並儲存新參數 -> (q={}, s={})", outputFile.getFileName(), String.format("%.3f", newParams.quality()), String.format("%.2f", newParams.scale()));

                    return true; // 成功，直接返回
                }
                // =======================================================
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

    /**
     * 使用二分搜尋法尋找能滿足目標檔案大小的最佳壓縮品質。
     *
     * @param image              要壓縮的圖片
     * @param targetMaxSizeBytes 目標檔案大小上限
     * @param initialQuality     初始的最高品質（搜尋範圍的上限）
     * @return 找到的最佳品質，如果找不到則返回 -1.0f
     * @throws IOException IO 錯誤
     */
    private static float findBestQualityByBinarySearch(BufferedImage image, long targetMaxSizeBytes, float initialQuality) throws IOException {
        log.trace("開始二分搜尋品質，目標大小: <= {}", FileTools.formatFileSize(targetMaxSizeBytes));
        float lowQuality = 0.0f;
        float highQuality = initialQuality;
        float bestQuality = -1.0f;

        // 通常 7-8 次迭代對於 0-1.0 的範圍已經有足夠的精度
        for (int i = 0; i < 8; i++) {
            float midQuality = (lowQuality + highQuality) / 2.0f;

            // 如果品質過低，可能沒有繼續搜尋的意義
            if (midQuality < 0.01f) {
                break;
            }

            long currentSize;
            // 使用預設大小來初始化，減少陣列複製
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream((int) targetMaxSizeBytes)) {
                compressJpgToStream(image, bos, midQuality);
                currentSize = bos.size();
            }

            log.trace(" 測試品質: {:.3f}, 檔案大小: {}", midQuality, FileTools.formatFileSize(currentSize));

            if (currentSize <= targetMaxSizeBytes) {
                // 成功！這個品質可用。記錄下來，並嘗試尋找更高的品質。
                bestQuality = midQuality;
                lowQuality = midQuality; // 移動下界，嘗試在 [mid, high] 區間尋找更好畫質
            } else {
                // 失敗！檔案還是太大。必須降低品質。
                highQuality = midQuality; // 移動上界，在 [low, mid] 區間繼續尋找
            }

            // 如果上下界已經非常接近，可以提前結束
            if ((highQuality - lowQuality) < 0.01f) {
                break;
            }
        }

        if (bestQuality > 0) {
            log.trace("二分搜尋找到最佳品質: {:.3f}", bestQuality);
        } else {
            log.trace("二分搜尋未能找到滿足條件的品質。");
        }

        return bestQuality;
    }
}
