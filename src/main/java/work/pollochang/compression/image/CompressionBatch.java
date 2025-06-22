package work.pollochang.compression.image;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.cache.H2CacheManager; // 【新增】Import H2CacheManager
import work.pollochang.compression.image.core.CompressionResult;
import work.pollochang.compression.image.core.ImageCompression;
import work.pollochang.compression.image.learn.LearnedParams;
import work.pollochang.compression.image.learn.jpg.SimilarityKey;
import work.pollochang.compression.image.report.CompressionParams;
import work.pollochang.compression.image.report.CompressionReport;
import work.pollochang.compression.image.tools.FileTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 進行批次壓縮
 */
@Setter
@Slf4j
public class CompressionBatch {

    private String fileListPath;
    private String saveDir;
    private CompressionParams compressionParams;
    private long timeOutHr;

    // 【修改】使用 h2CachePath 取代舊的 cachePath 和 Learn 物件
    private Path h2CachePath;

    public void execute() {
        Path outputDir = Paths.get(saveDir);
        Path inputListFile = Paths.get(fileListPath);
        FileTools.ensureDirectoryExists(outputDir);

        // 【修改】使用 H2CacheManager 來初始化 L1 記憶體快取
        H2CacheManager h2CacheManager = new H2CacheManager(h2CachePath);
        Map<SimilarityKey, LearnedParams> compressionCache = null;

        try {
            h2CacheManager.initSchema(); // 確保資料表存在
            compressionCache = h2CacheManager.loadAllToMap(); // 從 H2 載入快取
            log.info("初始化 H2 二級快取，並載入至記憶體 L1 快取。");

            // 計數器與執行緒池設定 (保持不變)
            Map<CompressionResult, AtomicLong> counters = new EnumMap<>(CompressionResult.class);
            for (CompressionResult result : CompressionResult.values()) {
                counters.put(result, new AtomicLong(0));
            }
            AtomicLong totalFiles = new AtomicLong(0);
            AtomicLong totalOriginalSize = new AtomicLong(0);
            AtomicLong totalCompressedSize = new AtomicLong(0);

            int coreCount = Math.max(1, Runtime.getRuntime().availableProcessors());
            log.info("偵測到 {} 個 CPU 核心，建立固定大小為 {} 的執行緒池。", coreCount, coreCount);

            // 執行緒池和任務提交邏輯 (保持不變)
            try (ExecutorService executor = Executors.newFixedThreadPool(coreCount)) {
                log.info("初始化固定大小執行緒執行器，將以 {} 的併發數量處理任務。", coreCount);

                final Map<SimilarityKey, LearnedParams> finalCompressionCache = compressionCache;
                try (Stream<String> lines = Files.lines(inputListFile)) {
                    lines.forEach(line -> {
                        if (line != null && !line.trim().isEmpty()) {
                            totalFiles.incrementAndGet();
                            Path inputPath = Paths.get(line.trim());
                            executor.submit(() -> {
                                // 傳入從 H2 載入的快取 Map
                                CompressionReport report = ImageCompression.processImage(
                                        inputPath,
                                        outputDir,
                                        compressionParams,
                                        finalCompressionCache
                                );
                                counters.get(report.result()).incrementAndGet();
                                totalOriginalSize.addAndGet(report.originalSize());
                                totalCompressedSize.addAndGet(report.compressedSize());
                            });
                        }
                    });
                } catch (IOException e) {
                    log.error("讀取檔案列表失敗: {}", fileListPath, e);
                    return;
                }

                log.info("所有任務已提交，等待處理完成...");
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(timeOutHr, TimeUnit.HOURS)) {
                        log.warn("執行緒池等待逾時，部分任務可能未完成。");
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.error("執行緒池被中斷。", e);
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 最終統計報告 (保持不變)
            log.info("所有圖片處理完成！");
            long successCount = counters.get(CompressionResult.COMPRESSED_SUCCESS).get();
            long skippedCount = counters.get(CompressionResult.SKIPPED_CONDITION_NOT_MET).get()
                    + counters.get(CompressionResult.SKIPPED_NOT_FOUND).get();
            long failedCount = totalFiles.get() - successCount - skippedCount;

            log.info("處理結果 -> 總計: {}, 成功壓縮: {}, 跳過不壓縮: {}, 失敗: {}",
                    totalFiles.get(),
                    successCount,
                    skippedCount,
                    failedCount);

            long finalOriginalSize = totalOriginalSize.get();
            long finalCompressedSize = totalCompressedSize.get();
            long savedSpace = finalOriginalSize - finalCompressedSize;
            double savedPercentage = (finalOriginalSize == 0) ? 0.0 : (double) savedSpace / finalOriginalSize * 100.0;

            log.info("========================================空間統計報告========================================");
            log.info(" 原始檔案總大小: {}", FileTools.formatFileSize(finalOriginalSize));
            log.info(" 壓縮後檔案總大小: {}", FileTools.formatFileSize(finalCompressedSize));
            log.info(" 共節省硬碟空間: {}", FileTools.formatFileSize(savedSpace));
            log.info(" 總空間節省百分比: {} %", String.format("%.2f", savedPercentage));
            log.info("========================================空間統計報告========================================");

        } catch (Exception e) {
            log.error("執行批次壓縮時發生未預期錯誤", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            // 【修改】在所有任務結束後，將記憶體快取回存至 H2，並關閉連線
            if (compressionCache != null) {
                log.info("記憶體中 L1 快取最終大小: {}", compressionCache.size());
                h2CacheManager.saveAllFromMap(compressionCache);
            }
            h2CacheManager.close();
        }
    }
}