package work.pollochang.compression.image;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
    private ImageCompression.CompressionParams compressionParams;
    private long timeOutHr;

    public void execute() {
        Path outputDir = Paths.get(saveDir);
        Path inputListFile = Paths.get(fileListPath);

        FileTools.ensureDirectoryExists(outputDir);

        // 使用 EnumMap 和 AtomicLong 進行線程安全的計數
        Map<CompressionResult, AtomicLong> counters = new EnumMap<>(CompressionResult.class);
        for (CompressionResult result : CompressionResult.values()) {
            counters.put(result, new AtomicLong(0));
        }
        AtomicLong totalFiles = new AtomicLong(0);
        // 新增: 用於統計總檔案大小的原子變數
        AtomicLong totalOriginalSize = new AtomicLong(0);
        AtomicLong totalCompressedSize = new AtomicLong(0);

        int coreCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        log.info("偵測到 {} 個 CPU 核心，建立固定大小為 {} 的執行緒池。", coreCount, coreCount);
        try (ExecutorService executor = Executors.newFixedThreadPool(coreCount)) {
            log.info("初始化固定大小執行緒執行器，將以 {} 的併發數量處理任務。", coreCount);
            log.info("初始化虛擬執行緒執行器，將為每個檔案處理任務建立一個虛擬執行緒。");


            // 使用 Stream API 逐行讀取檔案，避免一次性將整個列表載入記憶體
            try (Stream<String> lines = Files.lines(inputListFile)) {
                lines.forEach(line -> {
                    if (line != null && !line.trim().isEmpty()) {
                        totalFiles.incrementAndGet();
                        Path inputPath = Paths.get(line.trim());
                        executor.submit(() -> {
                            // 接收 CompressionReport 而不是 CompressionResult
                            ImageCompression.CompressionReport report = ImageCompression.processImage(inputPath, outputDir, compressionParams);

                            // 更新計數器
                            counters.get(report.result()).incrementAndGet();

                            // 累加檔案大小
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
                // 等待所有任務完成，最多等待數小時
                if (!executor.awaitTermination(timeOutHr, TimeUnit.HOURS)) {
                    log.warn("執行緒池等待逾時，部分任務可能未完成。");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("執行緒池被中斷。", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt(); // 恢復中斷狀態
            }
        }

        log.info("所有圖片處理完成！");
        // 使用正確的計數器產生最終報告
        long successCount = counters.get(CompressionResult.COMPRESSED_SUCCESS).get();
        long skippedCount = counters.get(CompressionResult.SKIPPED_CONDITION_NOT_MET).get()
                + counters.get(CompressionResult.SKIPPED_NOT_FOUND).get();
        long failedCount = totalFiles.get() - successCount - skippedCount;

        log.info("處理結果 -> 總計: {}, 成功壓縮: {}, 跳過不壓縮: {}, 失敗: {}",
                totalFiles.get(),
                successCount,
                skippedCount,
                failedCount);

        // 新增的最終統計報告
        long finalOriginalSize = totalOriginalSize.get();
        long finalCompressedSize = totalCompressedSize.get();
        long savedSpace = finalOriginalSize - finalCompressedSize;
        double savedPercentage = (finalOriginalSize == 0) ? 0.0 : (double) savedSpace / finalOriginalSize * 100.0;

        log.info("========================================空間統計報告========================================");
        log.info(" 原始檔案總大小: {}", ImageCompression.formatFileSize(finalOriginalSize));
        log.info(" 壓縮後檔案總大小: {}", ImageCompression.formatFileSize(finalCompressedSize));
        log.info(" 共節省硬碟空間: {}", ImageCompression.formatFileSize(savedSpace));
        log.info(" 總空間節省百分比: {} %", savedPercentage);
        log.info("========================================空間統計報告========================================");
    }
}