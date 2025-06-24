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

import static work.pollochang.compression.image.tools.CacheTools.createKey;
import static work.pollochang.compression.image.tools.ImageTools.resizeImage;

/**
 * JPEG 圖片壓縮工具類，提供根據目標檔案大小進行品質與尺寸調整的壓縮邏輯。
 *
 * <p>本類別支援以下功能：
 * <ul>
 *   <li>根據最大目標檔案大小，自動尋找最佳 JPEG 壓縮品質。</li>
 *   <li>依據歷史學習（快取）資料加速壓縮流程。</li>
 *   <li>若快取失效，會進行圖片縮放與二分搜尋品質的標準壓縮流程。</li>
 * </ul>
 *
 * <p>壓縮流程大致如下：
 * <ol>
 *   <li>計算圖片特徵並查詢快取，若命中則嘗試直接壓縮。</li>
 *   <li>若快取無效，透過遞減縮放比例與二分搜尋 JPEG 品質達成壓縮目標。</li>
 *   <li>成功壓縮後會更新快取，提升下次壓縮效率。</li>
 * </ol>
 *
 * <p>本類別適用於圖庫管理、媒體處理、上傳優化等場景，並可搭配 {@link CompressionParams} 與 {@link LearnedParams}
 * 等結構配合使用。</p>
 *
 * <p>使用範例：
 * <pre>{@code
 * BufferedImage image = ImageIO.read(...);
 * Path output = Paths.get("out.jpg");
 * CompressionParams params = new CompressionParams(0.85f, 100 * 1024); // 100 KB
 * Map<SimilarityKey, LearnedParams> cache = new HashMap<>();
 * boolean success = ImageCompressionJpg.compressJpgWithTargetSize(image, size, output, params, cache);
 * }</pre>
 *
 * @author PolloChang
 * @since 0.1.0
 */
@Slf4j
public class ImageCompressionJpg {

    /**
     * 將指定的 {@link BufferedImage} 壓縮為 JPEG 格式，並嘗試在不超過目標檔案大小的情況下輸出到指定路徑。
     *
     * <p>此方法會優先使用快取中學習到的最佳壓縮參數（品質與縮放比例）進行快速壓縮；
     * 若無法滿足目標大小，則會透過多輪圖片縮放與品質二分搜尋，嘗試動態尋找一組可行的壓縮參數。</p>
     *
     * <p>壓縮成功後，會將圖片寫入指定的 {@code outputFile}，並將該參數加入學習快取中，
     * 以利未來處理相似圖片時直接重用。</p>
     *
     * @param originalImage        原始 {@link BufferedImage} 圖片，需為非 null。
     * @param originalSize         原始圖片檔案大小（bytes），用於建立快取鍵。
     * @param outputFile           壓縮後要寫入的檔案路徑。
     * @param params               壓縮參數，包含目標檔案大小與初始品質等資訊。
     * @param cache                快取物件，用來儲存已學習的壓縮參數，key 為 {@link SimilarityKey}。
     * @return                     若成功在目標大小限制內完成壓縮並寫入檔案，則回傳 {@code true}，否則回傳 {@code false}。
     * @throws IOException         當發生 I/O 錯誤（如檔案寫入失敗、壓縮過程錯誤等）時拋出。
     */
    public static boolean compressJpgWithTargetSize(BufferedImage originalImage, long originalSize, Path outputFile, CompressionParams params, Map<SimilarityKey, LearnedParams> cache) throws IOException {
        // 1. 產生快取 Key
        SimilarityKey key = createKey(originalImage, originalSize);
        LearnedParams cachedParams = cache.get(key);

        if (cachedParams != null) {
            if (tryCachedParams(originalImage, outputFile, params, cachedParams)) {
                log.info("快取成功: {} 使用學習參數直接達成目標。", outputFile.getFileName());
                return true;
            } else {
                log.warn("快取失效: {} 使用學習參數後檔案仍超標，退回標準流程。", outputFile.getFileName());
            }
        }

        final double SCALE_STEP = 0.85;
        BufferedImage currentImage = originalImage;
        boolean isOriginal = true;

        try {
            for (double scale = 1.0; scale > 0.1; scale = (scale == 1.0) ? SCALE_STEP : scale * SCALE_STEP) {
                if (scale < 1.0) {
                    if (!isOriginal) currentImage.flush();
                    currentImage = resizeImage(originalImage, scale);
                    isOriginal = false;
                    log.debug("檔案仍然過大，縮放至 {}%", (int) (scale * 100));
                }

                // ==================== 【修改核心】 ====================
                // 刪除舊的 for-loop，改為呼叫二分搜尋法
                float bestQuality = findBestQualityByBinarySearch(currentImage, params.targetMaxSizeBytes(), params.quality());

                // 如果找到了合適的品質 (bestQuality > 0)
                if (bestQuality > 0) {
                    saveCompressedImage(currentImage, outputFile, bestQuality, params.targetMaxSizeBytes());
                    cache.put(key, new LearnedParams(bestQuality, scale));
                    log.info("{} - 學習並儲存新參數 -> (q={}, s={})", outputFile.getFileName(), String.format("%.3f", bestQuality), String.format("%.2f", scale));
                    return true;
                }
            }
        } finally {
            if (!isOriginal && currentImage != null) currentImage.flush();
        }

        log.warn("無法在目標大小限制下完成壓縮: {}", outputFile.getFileName());
        return false;
    }

    /**
     * 將指定的 {@link BufferedImage} 圖像以指定的壓縮品質轉換為 JPEG 格式，
     * 並寫入至指定的 {@link OutputStream} 輸出串流。
     *
     * <p>此方法會使用 Java 的 {@link ImageIO} API 和 {@link ImageWriter}
     * 對圖像進行 JPEG 壓縮，並設置明確的壓縮模式與品質等級。</p>
     *
     * @param image   要壓縮的圖片，必須為非 null 的 {@link BufferedImage}。
     * @param os      輸出串流，用來接收 JPEG 壓縮後的影像資料。
     * @param quality 壓縮品質，數值範圍為 0.0f（最低品質，最大壓縮）到 1.0f（最高品質，最小壓縮）。
     * @throws IOException 如果在建立輸出串流或圖像寫入過程中發生 I/O 錯誤。
     */
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
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream((int) targetMaxSizeBytes)) {
            // 通常 7-8 次迭代對於 0-1.0 的範圍已經有足夠的精度
            for (int i = 0; i < 8; i++) {
                float midQuality = (lowQuality + highQuality) / 2.0f;

                if (midQuality < 0.01f) {
                    break;
                }

                bos.reset(); // 【優化】重置串流，準備下次寫入
                compressJpgToStream(image, bos, midQuality);
                long currentSize = bos.size();

                log.trace(" 測試品質: {:.3f}, 檔案大小: {}", midQuality, FileTools.formatFileSize(currentSize));

                if (currentSize <= targetMaxSizeBytes) {
                    bestQuality = midQuality;
                    lowQuality = midQuality;
                } else {
                    highQuality = midQuality;
                }

                if ((highQuality - lowQuality) < 0.01f) {
                    break;
                }
            }
        }

        if (bestQuality > 0) {
            log.trace("二分搜尋找到最佳品質: {:.3f}", bestQuality);
        } else {
            log.trace("二分搜尋未能找到滿足條件的品質。");
        }

        return bestQuality;
    }

    /**
     * 嘗試使用快取中的學習參數（品質與縮放比例）對原始圖片進行壓縮，
     * 並判斷是否能在目標檔案大小限制內成功輸出壓縮結果。
     *
     * <p>本方法主要用於優先使用過去已學習到的參數來進行壓縮，加速壓縮流程，
     * 若壓縮結果符合 {@code params.targetMaxSizeBytes()} 的限制，即視為成功並輸出圖片至指定路徑。</p>
     *
     * @param originalImage 原始未壓縮的 {@link BufferedImage} 影像。
     * @param outputFile    壓縮後輸出的檔案路徑。
     * @param params        壓縮參數，包含目標檔案大小等限制。
     * @param cachedParams  快取中學習到的壓縮參數（品質與縮放比例）。
     * @return 如果使用快取參數成功壓縮並寫入檔案且滿足檔案大小限制，則回傳 {@code true}；否則回傳 {@code false}。
     * @throws IOException 如果在壓縮或寫入檔案過程中發生 I/O 錯誤時拋出。
     */
    private static boolean tryCachedParams(BufferedImage originalImage, Path outputFile, CompressionParams params,
                                           LearnedParams cachedParams) throws IOException {
        BufferedImage imageToCompress = originalImage;
        boolean resized = false;

        if (cachedParams.scale() < 1.0) {
            imageToCompress = resizeImage(originalImage, cachedParams.scale());
            resized = true;
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            compressJpgToStream(imageToCompress, bos, cachedParams.quality());
            if (bos.size() <= params.targetMaxSizeBytes()) {
                Files.write(outputFile, bos.toByteArray());
                return true;
            }
        } finally {
            if (resized && imageToCompress != null) imageToCompress.flush();
        }

        log.warn("無法在目標大小限制下完成壓縮: {}", outputFile.getFileName());
        return false;
    }


    /**
     * 將指定的 {@link BufferedImage} 以指定的壓縮品質進行 JPEG 壓縮，並儲存至指定的輸出檔案。
     *
     * <p>該方法會先將影像以指定品質壓縮至記憶體中的 {@link ByteArrayOutputStream}，
     * 然後再一次性寫入到檔案系統中的 {@link Path outputFile}。此方式可以有效控制檔案大小，
     * 並避免直接寫入磁碟造成不必要的 I/O 開銷。</p>
     *
     * @param image         要壓縮的 {@link BufferedImage} 圖片物件。
     * @param outputFile    壓縮後輸出的檔案路徑。
     * @param quality       壓縮品質，範圍為 0.0f（最低）到 1.0f（最高）。
     * @param estimatedSize 預估的輸出檔案大小（以 byte 為單位），
     *                      用於初始化 {@link ByteArrayOutputStream} 的容量，避免記憶體重分配。
     * @throws IOException 當壓縮或寫入檔案時發生 I/O 錯誤時拋出。
     */
    private static void saveCompressedImage(BufferedImage image, Path outputFile, float quality, long estimatedSize) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream((int) estimatedSize)) {
            compressJpgToStream(image, bos, quality);
            Files.write(outputFile, bos.toByteArray());
        }
    }

}
