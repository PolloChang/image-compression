package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 圖片壓縮程式
 */
@Slf4j
public class ImageCompression {


    /**
     * 壓縮圖片檔案
     * @param inputPath   來源圖片檔案
     * @param outputDir  輸出圖片檔案
     * @param quality     壓縮品質 (0.0f 到 1.0f 之間)
     */
    public void processImage(Path inputPath, Path outputDir, float quality) {
        if (!Files.exists(inputPath)) {
            log.warn("檔案不存在，跳過處理: {}", inputPath);
            return;
        }

        String fileName = inputPath.getFileName().toString();
        File outputFile = outputDir.resolve(fileName).toFile();
        File inputFile = inputPath.toFile();

        try {
            log.info("開始壓縮檔案: {} -> {}", inputPath, outputFile.getAbsolutePath());

            // 使用 thumbnailator 進行壓縮
            Thumbnails.of(inputFile)
                    .scale(1.0) // 保持原始尺寸
                    .outputQuality(quality) // 設定壓縮品質
                    .toFile(outputFile);

            log.info("圖片已成功壓縮並儲存至: {}", outputFile.getAbsolutePath());

        } catch (IOException e) {
            // thumbnailator 可能會因為無法讀取檔案格式而拋出 IOException
            log.warn("無法處理圖片檔案 (可能非支援格式或檔案已損毀): {}", inputPath, e);
        } catch (Exception e) {
            log.error("處理檔案時發生未知錯誤: {}", inputPath, e);
        }
    }
}