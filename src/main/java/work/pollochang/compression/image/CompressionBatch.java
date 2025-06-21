package work.pollochang.compression.image;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 進行批次壓縮 (使用傳統的 FixedThreadPool)
 */
@Setter
@Slf4j
public class CompressionBatch {

    private String fileListPath;

    private String saveDir;

    private ImageCompression.CompressionParams compressionParams;

    /**
     * 執行
     */
    public void execute(){
        Path outputDir = Paths.get(saveDir);
        Path inputListFile = Paths.get(fileListPath);

        FileTools.ensureDirectoryExists(outputDir); // FileTools 類別可保持不變

        // 使用 EnumMap 和 AtomicLong 進行線程安全的計數
        Map<ImageCompression.CompressionResult, AtomicLong> counters = new EnumMap<>(ImageCompression.CompressionResult.class);
        for (ImageCompression.CompressionResult result : ImageCompression.CompressionResult.values()) {
            counters.put(result, new AtomicLong(0));
        }
        AtomicLong totalFiles = new AtomicLong(0);

        // 讀取檔案列表
        List<Path> imagePaths;
        try {
            imagePaths = Files.lines(Paths.get(fileListPath))
                    .map(Paths::get)
                    .collect(Collectors.toList());
            log.info("從 {} 讀取了 {} 個檔案路徑。", fileListPath, imagePaths.size());
        } catch (IOException e) {
            log.error("讀取檔案列表失敗: " + fileListPath, e);
            return;
        }



        // 使用執行緒池平行處理
        int threads = Runtime.getRuntime().availableProcessors(); // 使用與CPU核心數相同的執行緒數量
        // 使用原子計數器在多執行緒環境下安全地計數
        AtomicInteger successCompressedCount = new AtomicInteger(0);
        AtomicInteger notCompressedCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        log.info("初始化執行緒池，使用 {} 個執行緒。", threads);

        ////
        // 使用 Stream API 逐行讀取檔案，避免一次性載入記憶體
        try (Stream<String> lines = Files.lines(inputListFile)) {
            lines.forEach(line -> {
                totalFiles.incrementAndGet();
                Path inputPath = Paths.get(line.trim());
                // 提交任務到執行緒池
                executor.submit(() -> {
                    ImageCompression.CompressionResult result = ImageCompression.processImage(inputPath, outputDir, compressionParams);
                    counters.get(result).incrementAndGet();
                });
            });
        } catch (IOException e) {
            log.error("讀取檔案列表失敗: {}", fileListPath, e);
            return;
        }

        // 虛擬執行緒池的關閉邏輯與傳統執行緒池稍有不同，
        // 通常 submit 後就可以直接調用 shutdown，然後 awaitTermination。
        log.info("所有任務已提交，等待處理完成...");
        executor.shutdown();

        ////

        try {
            // 等待所有任務完成，最多等待1小時
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                log.warn("執行緒池等待逾時，部分任務可能未完成。");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("執行緒池被中斷。", e);
            executor.shutdownNow();
        }

        log.info("所有圖片處理完成！");
        log.info("處理結果 -> 總計: {}, 成功壓縮: {} , 不壓縮: {} , 失敗: {}", imagePaths.size(), successCompressedCount.get(), notCompressedCount.get() , failureCount.get());
    }
}