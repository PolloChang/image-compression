package work.pollochang.compression.image;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private Path cachePath;

    public void execute() {
        Path outputDir = Paths.get(saveDir);
        Path inputListFile = Paths.get(fileListPath);

        FileTools.ensureDirectoryExists(outputDir);

        // 建立一個執行緒安全的共用快取
        final Map<SimilarityKey, LearnedParams> compressionCache;
        if (cachePath != null) {
            compressionCache = loadCacheFromFile(cachePath);
        } else {
            log.info("未指定快取檔案，將使用空的記憶體快取。");
            compressionCache = new ConcurrentHashMap<>();
        }

        log.info("初始化壓縮參數學習快取。");

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
                            ImageCompression.CompressionReport report = ImageCompression.processImage(
                                    inputPath,
                                    outputDir,
                                    compressionParams,
                                    compressionCache // 傳入快取
                            );

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
        log.info("壓縮參數快取最終大小: {}", compressionCache.size());

        // 在所有任務結束後，儲存快取
        if (cachePath != null) {
            saveCacheToFile(cachePath, compressionCache);
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

    /**
     * 從指定的 JSON 檔案讀取並還原快取。
     * @param path 快取檔案的路徑
     * @return 一個 ConcurrentHashMap，如果檔案不存在或讀取失敗則為空。
     */
    private Map<SimilarityKey, LearnedParams> loadCacheFromFile(Path path) {
        if (Files.exists(path)) {
            ObjectMapper mapper = new ObjectMapper();

            // 1. 建立一個模組
            SimpleModule module = new SimpleModule();
            // 2. 告訴模組，當遇到需要將 key 轉成 SimilarityKey 的情況時，請使用我們自訂的 Deserializer
            module.addKeyDeserializer(SimilarityKey.class, new SimilarityKeyDeserializer());
            // 3. 將這個模組註冊到 ObjectMapper 中
            mapper.registerModule(module);

            try {
                // 使用 TypeReference 來讓 Jackson 知道要轉換成的複雜 Map 型別
                Map<SimilarityKey, LearnedParams> loadedMap = mapper.readValue(path.toFile(), new TypeReference<>() {});
                log.info("成功從 {} 讀取 {} 筆學習快取紀錄。", path, loadedMap.size());
                // 返回一個 ConcurrentHashMap 以確保執行緒安全
                return new ConcurrentHashMap<>(loadedMap);
            } catch (IOException e) {
                log.warn("讀取快取檔案 {} 失敗，將使用新的空快取。", path, e);
            }
        } else {
            log.info("快取檔案 {} 不存在，將建立新的空快取。", path);
        }
        return new ConcurrentHashMap<>();
    }

    /**
     * 將記憶體中的快取儲存到指定的 JSON 檔案。
     * @param path 快取檔案的路徑
     * @param cache 要儲存的快取 Map
     */
    private void saveCacheToFile(Path path, Map<SimilarityKey, LearnedParams> cache) {
        if (cache == null || cache.isEmpty()) {
            log.info("快取為空，無需儲存。");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // 讓 JSON 格式化，方便閱讀

        try {
            log.info("正在將 {} 筆快取紀錄儲存至 {} ...", cache.size(), path);
            mapper.writeValue(path.toFile(), cache);
            log.info("快取成功儲存。");
        } catch (IOException e) {
            log.error("儲存快取至檔案 {} 時發生錯誤。", path, e);
        }
    }
}