package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.io.FileInputStream;


/**
 * 圖片壓縮工具類 (重構為無狀態)
 */
@Slf4j
public final class ImageCompression {

    // 使用 record 來封裝壓縮任務的參數，使程式碼更清晰
    public record CompressionParams(float quality, long minSizeBytes, int minWidth, int minHeight) {}

    // 私有化建構子，防止實例化
    private ImageCompression() {}

    /**
     * 處理單一圖片的壓縮流程
     * @param inputPath 來源圖片路徑
     * @param outputDir 輸出目錄
     * @param params 壓縮參數
     * @return 處理結果的狀態
     */
    public static CompressionResult processImage(Path inputPath, Path outputDir, CompressionParams params) {
        if (!Files.exists(inputPath)) {
            log.warn("檔案不存在，跳過: {}", inputPath);
            return CompressionResult.SKIPPED_NOT_FOUND;
        }

        try {
            // 進行壓縮前的條件檢查
            if (!shouldCompressImage(inputPath, params)) {
                return CompressionResult.SKIPPED_CONDITION_NOT_MET;
            }

            long originalSize = Files.size(inputPath);
            Path outputFile = outputDir.resolve(inputPath.getFileName());

            log.info("開始壓縮: {}", inputPath);
            boolean success = compressImage(inputPath, outputFile, params.quality());

            if (success) {
                long compressedSize = Files.size(outputFile);
                double ratio = 100.0 * (originalSize - compressedSize) / originalSize;
                log.info("壓縮成功: {} -> {} (大小: {} -> {}, 節省: {:.2f}%)",
                        inputPath.getFileName(), outputFile.getFileName(),
                        formatFileSize(originalSize), formatFileSize(compressedSize), ratio);
                return CompressionResult.COMPRESSED_SUCCESS;
            } else {
                return CompressionResult.FAILED_COMPRESSION;
            }
        } catch (IOException e) {
            log.warn("處理圖片時發生 I/O 錯誤 (可能非支援格式或檔案損毀): {}", inputPath, e);
            return CompressionResult.FAILED_IO_ERROR;
        } catch (Exception e) {
            log.error("處理檔案時發生未知錯誤: {}", inputPath, e);
            return CompressionResult.FAILED_UNKNOWN;
        }
    }

    /**
     * 檢核圖片是否需要壓縮
     */
    private static boolean shouldCompressImage(Path inputPath, CompressionParams params) throws IOException {
        long fileSize = Files.size(inputPath);
        if (fileSize <= params.minSizeBytes()) {
            log.info("檔案大小 {} 未超過閾值 {}，跳過: {}", formatFileSize(fileSize), formatFileSize(params.minSizeBytes()), inputPath);
            return false;
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(inputPath.toFile())) {
            if (in == null) {
                log.warn("無法建立圖片輸入流，跳過: {}", inputPath);
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= params.minWidth() || height <= params.minHeight()) {
                        log.info("圖片尺寸 {}x{} 未超過閾值 {}x{}，跳過: {}", width, height, params.minWidth(), params.minHeight(), inputPath);
                        return false;
                    }
                } finally {
                    reader.dispose();
                }
            } else {
                log.warn("找不到對應的圖片讀取器，跳過: {}", inputPath);
                return false;
            }
        }
        return true;
    }

    /**
     * 根據檔案類型執行對應的壓縮方法
     */
    private static boolean compressImage(Path inputFile, Path outputFile, float quality) throws IOException {
        String fileType = getImageFileType(inputFile.toFile());
        if (fileType == null || !("jpg".equals(fileType) || "png".equals(fileType))) {
            log.warn("檔案類型 {} 不支援或無法識別，跳過壓縮: {}", fileType, inputFile);
            return false;
        }

        // ！！關鍵的記憶體瓶頸點！！
        // 這裡仍然需要將圖片讀入記憶體。在資源有限的環境下，這是最主要的風險。
        BufferedImage image = ImageIO.read(inputFile.toFile());
        if (image == null) {
            log.warn("ImageIO 無法讀取檔案 (可能已損毀): {}", inputFile);
            return false;
        }

        switch (fileType) {
            case "jpg":
                return compressImageJPG(image, outputFile.toFile(), quality);
            case "png":
                return resizeImagePNG(image, outputFile.toFile());
            default:
                return false;
        }
    }

    // ... (其他輔助方法如 compressImageJPG, resizeImagePNG, getImageFileType, formatFileSize 保持不變)
    // 為了簡潔，這裡省略與您原始碼中相同的輔助方法。請將您原有的 `compressImageJPG`, `resizeImagePNG`,
    // `getImageFileType`, `formatFileSize` 方法複製到這個類別中，並將它們改為 `private static`。

    // ... (此處應包含您原始檔案中的 compressImageJPG, resizeImagePNG 等方法，並加上 static 關鍵字)
    private static boolean compressImageJPG(BufferedImage image, File outputFile, float quality) throws IOException {
        log.debug("進行壓縮: JPG");
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("找不到可用的 JPG 圖片寫入器");
        }
        ImageWriter writer = writers.next();
        try (OutputStream os = Files.newOutputStream(outputFile.toPath());
             ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return true;
        } finally {
            writer.dispose();
        }
    }

    private static boolean resizeImagePNG(BufferedImage originalImage, File outputFile) throws IOException {
        log.debug("進行壓縮: PNG");
        int targetWidth = 1920;
        if (originalImage.getWidth() <= targetWidth) {
            log.debug("PNG 圖片寬度小於等於目標寬度，不進行縮放。");
            return false; // 或者直接複製檔案
        }
        int targetHeight = (int) (originalImage.getHeight() * ((double) targetWidth / originalImage.getWidth()));
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType());
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return ImageIO.write(resizedImage, "png", outputFile);
    }

    private static String getImageFileType(File file) throws IOException {
        // ... 與您版本相同 ...
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("無法讀取圖片檔案，可能非支援格式或檔案已損毀: {}", file.getAbsolutePath());
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magicBytes = new byte[8];
            int bytesRead = fis.read(magicBytes, 0, Math.min(magicBytes.length, (int) file.length()));
            if (bytesRead >= 8 && magicBytes[0] == (byte) 0x89 && magicBytes[1] == (byte) 0x50 && magicBytes[2] == (byte) 0x4E && magicBytes[3] == (byte) 0x47 && magicBytes[4] == (byte) 0x0D && magicBytes[5] == (byte) 0x0A && magicBytes[6] == (byte) 0x1A && magicBytes[7] == (byte) 0x0A) {
                return "png";
            }
            if (bytesRead >= 4 && magicBytes[0] == (byte) 0xFF && magicBytes[1] == (byte) 0xD8 && magicBytes[2] == (byte) 0xFF && (magicBytes[3] == (byte) 0xE0 || magicBytes[3] == (byte) 0xE1 || magicBytes[3] == (byte) 0xE8)) {
                return "jpg";
            }
        }
        return "UNKNOWN";
    }

    static String formatFileSize(long size) {
        // ... 與您版本相同 ...
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    // 新增一個 enum 來表示壓縮結果，使計數更清晰
    public enum CompressionResult {
        COMPRESSED_SUCCESS,
        SKIPPED_CONDITION_NOT_MET,
        SKIPPED_NOT_FOUND,
        FAILED_COMPRESSION,
        FAILED_IO_ERROR,
        FAILED_UNKNOWN
    }
}