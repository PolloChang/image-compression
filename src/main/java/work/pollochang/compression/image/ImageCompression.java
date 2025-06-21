package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * 圖片壓縮程式
 */
@Slf4j
public class ImageCompression {


    /**
     * 壓縮圖片檔案
     *
     * @param inputFile   來源圖片檔案
     * @param outputFile  輸出圖片檔案
     * @param quality     壓縮品質 (0.0f 到 1.0f 之間)
     * @throws IOException 檔案讀寫發生錯誤時拋出
     * @throws IllegalArgumentException 壓縮品質參數錯誤時拋出
     */
    private void compressImage(File inputFile, File outputFile, float quality) throws IOException {
        // 1. 讀取來源圖片
        BufferedImage image = ImageIO.read(inputFile);
        if (image == null) {
            log.warn("無法讀取圖片檔案，可能非支援格式或檔案已損毀: {}", inputFile.getAbsolutePath());
            return;
        }

        // 2. 尋找合適的 ImageWriter (這裡我們使用 JPEG)
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("找不到可用的 JPG 圖片寫入器");
        }
        ImageWriter writer = writers.next();

        // 3. 準備輸出串流 (使用 try-with-resources)
        try (OutputStream os = new FileOutputStream(outputFile);
             ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {

            writer.setOutput(ios);

            // 4. 設定壓縮參數
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                log.debug("設定壓縮品質為: {}", quality);
            }

            // 5. 寫入壓縮後的圖片
            writer.write(null, new IIOImage(image, null, null), param);
            log.info("圖片已成功壓縮並儲存至: {}", outputFile.getAbsolutePath());

        } finally {
            // 6. 清理資源
            writer.dispose();
        }
    }

    public void processImage(Path inputPath, Path outputDir, float quality) {
        if (!Files.exists(inputPath)) {
            log.warn("檔案不存在，跳過處理: {}", inputPath);
            return;
        }

        try {
            String fileName = inputPath.getFileName().toString();
            Path outputPath = outputDir.resolve(fileName); // 使用 resolve 安全地組合路徑

            File inputFile = inputPath.toFile();
            File outputFile = outputPath.toFile();

            log.info("開始壓縮檔案: {} -> {}", inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
            compressImage(inputFile, outputFile, quality);

        } catch (IOException e) {
            log.error("處理檔案時發生 I/O 錯誤: {}", inputPath, e);
        } catch (Exception e) {
            log.error("處理檔案時發生未知錯誤: {}", inputPath, e);
        }
    }
}