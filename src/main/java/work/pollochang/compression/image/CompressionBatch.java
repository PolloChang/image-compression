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
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        log.info("初始化執行緒池，使用 {} 個執行緒。", threads);

        for (Path inputPath : imagePaths) {
            // 提交任務到執行緒池
            executor.submit(() -> {
                imageCompression.processImage(inputPath,outputDir,quality );
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
    }

}
