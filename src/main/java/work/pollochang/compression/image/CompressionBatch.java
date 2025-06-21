package work.pollochang.compression.image;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 進行批次壓縮
 */
@Slf4j
public class CompressionBatch {

    @Setter
    private String fileListPath;

    @Setter
    private String saveDir;

    @Setter
    private float quality;

    /**
     * 執行
     */
    public void execute(){
        FileTools fileTools = new FileTools();
        ImageCompression imageCompression = new ImageCompression();
        Path outputDir = Paths.get(saveDir);

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

        fileTools.ensureDirectoryExists(outputDir);

        // 使用執行緒池平行處理
        int threads = Runtime.getRuntime().availableProcessors(); // 使用與CPU核心數相同的執行緒數量
        // 使用原子計數器在多執行緒環境下安全地計數
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        log.info("初始化執行緒池，使用 {} 個執行緒。", threads);

        for (Path inputPath : imagePaths) {
            // 提交任務到執行緒池
            executor.submit(() -> {
                try {
                    // processImage 應返回一個布林值或透過異常來判斷成功與否
                    // 假設我們修改 processImage，讓它在成功時返回 true
                    imageCompression.processImage(inputPath, outputDir, quality);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn(e.getMessage());
                    failureCount.incrementAndGet();
                    // 錯誤已在 processImage 中記錄，這裡只需計數
                }
            });
        }
        // 關閉執行緒池
        log.info("所有任務已提交，等待處理完成...");
        executor.shutdown();

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
        log.info("處理結果 -> 總計: {}, 成功: {}, 失敗: {}", imagePaths.size(), successCount.get(), failureCount.get());
    }

}
