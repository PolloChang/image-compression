package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Iterator;

/**
 * 圖片壓縮工具類 (重構為無狀態)
 */
@Slf4j
public final class ImageCompression {

    public record CompressionParams(float quality, long minSizeBytes, int minWidth, int minHeight, long targetMaxSizeBytes) {}

    private ImageCompression() {}

    public static CompressionResult processImage(Path inputPath, Path outputDir, CompressionParams params) {
        if (!Files.exists(inputPath)) {
            log.warn("檔案不存在，跳過: {}", inputPath);
            return CompressionResult.SKIPPED_NOT_FOUND;
        }
        try {
            if (!shouldCompressImage(inputPath, params)) {
                return CompressionResult.SKIPPED_CONDITION_NOT_MET;
            }

            long originalSize = Files.size(inputPath);
            Path outputFile = outputDir.resolve(inputPath.getFileName());

            log.info("開始處理: {}", inputPath);
            boolean success = compressImageIteratively(inputPath, outputFile, params);

            if (success) {
                long compressedSize = Files.size(outputFile);
                double ratio = 100.0 * (originalSize - compressedSize) / originalSize;
                log.info("處理成功: {} -> {} (大小: {} -> {}, 節省: {:.2f}%)",
                        inputPath.getFileName(), outputFile.getFileName(),
                        formatFileSize(originalSize), formatFileSize(compressedSize), ratio);
                return CompressionResult.COMPRESSED_SUCCESS;
            } else {
                log.warn("無法在目標大小限制下完成壓縮: {}", inputPath);
                return CompressionResult.FAILED_COMPRESSION;
            }
        } catch (IOException e) {
            log.warn("處理圖片時發生 I/O 錯誤 (可能非支援格式或檔案損毀): {}", inputPath, e);
            return CompressionResult.FAILED_IO_ERROR;
        } catch (OutOfMemoryError e) {
            log.error("處理檔案時發生記憶體溢位錯誤 (圖片可能過大): {}", inputPath, e);
            return CompressionResult.FAILED_OUT_OF_MEMORY;
        } catch (Exception e) {
            log.error("處理檔案時發生未知錯誤: {}", inputPath, e);
            return CompressionResult.FAILED_UNKNOWN;
        }
    }

    private static boolean shouldCompressImage(Path inputPath, CompressionParams params) throws IOException {
        long fileSize = Files.size(inputPath);
        if (fileSize <= params.minSizeBytes()) {
            log.info("檔案大小 {} 未超過最小壓縮門檻 {}，跳過: {}", formatFileSize(fileSize), formatFileSize(params.minSizeBytes()), inputPath);
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
                        log.info("圖片尺寸 {}x{} 未超過最小壓縮門檻 {}x{}，跳過: {}", width, height, params.minWidth(), params.minHeight(), inputPath);
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
     * 【記憶體瓶頸警告】
     * 無論如何優化，`ImageIO.read()` 都會將解碼後的圖片完整載入記憶體。
     * 對於高解析度圖片（例如 4K 或更高），這可能消耗數百 MB 的堆記憶體。
     * 在記憶體受限的環境下，這是造成 OutOfMemoryError 的主要風險點。
     */
    private static boolean compressImageIteratively(Path inputFile, Path outputFile, CompressionParams params) throws IOException {
        String fileType = getImageFileType(inputFile.toFile());
        if (fileType == null || !("jpg".equals(fileType) || "png".equals(fileType))) {
            log.warn("檔案類型 {} 不支援或無法識別，跳過壓縮: {}", fileType, inputFile);
            return false;
        }

        BufferedImage image = ImageIO.read(inputFile.toFile());
        if (image == null) {
            log.warn("ImageIO 無法讀取檔案 (可能已損毀): {}", inputFile);
            return false;
        }

        try {
            switch (fileType) {
                case "jpg":
                    return compressJpgWithTargetSize(image, outputFile, params);
                case "png":
                    return compressPngWithTargetSize(image, outputFile, params);
                default:
                    return false;
            }
        } finally {
            image.flush(); // 釋放圖片佔用的資源
        }
    }

    private static boolean compressJpgWithTargetSize(BufferedImage originalImage, Path outputFile, CompressionParams params) throws IOException {
        final float MIN_QUALITY = 0.1f;
        final float QUALITY_STEP = 0.1f;
        final double SCALE_STEP = 0.9; // 每次縮小 10%

        BufferedImage currentImage = originalImage;

        for (double scale = 1.0; scale > 0.2; scale -= (1.0 - SCALE_STEP)) { // 尺寸縮小下限為原始的 20%
            if (scale < 1.0) {
                log.debug("圖片仍然過大，縮小尺寸至 {}%", (int) (scale * 100));
                currentImage = resizeImage(originalImage, scale);
            }

            for (float quality = params.quality(); quality >= MIN_QUALITY; quality -= QUALITY_STEP) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    compressJpgToStream(currentImage, bos, quality);
                    if (bos.size() <= params.targetMaxSizeBytes()) {
                        log.debug("找到合適參數: quality={}, scale={}", String.format("%.2f", quality), String.format("%.2f", scale));
                        Files.write(outputFile, bos.toByteArray());
                        return true;
                    }
                }
            }
            if(currentImage != originalImage) {
                currentImage.flush();
            }
        }
        return false;
    }

    private static void compressJpgToStream(BufferedImage image, OutputStream os, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("找不到 JPG 寫入器");
        ImageWriter writer = writers.next();
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


    private static boolean compressPngWithTargetSize(BufferedImage originalImage, Path outputFile, CompressionParams params) throws IOException {
        final double SCALE_STEP = 0.95; // 每次縮小 5%

        for (double scale = 1.0; scale > 0.2; scale -= (1.0 - SCALE_STEP)) {
            BufferedImage resizedImage = (scale == 1.0) ? originalImage : resizeImage(originalImage, scale);
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ImageIO.write(resizedImage, "png", bos);
                if (bos.size() <= params.targetMaxSizeBytes()) {
                    log.debug("找到合適尺寸: scale={}", String.format("%.2f", scale));
                    Files.write(outputFile, bos.toByteArray());
                    return true;
                }
            } finally {
                if (resizedImage != originalImage) {
                    resizedImage.flush();
                }
            }
        }
        return false;
    }


    private static BufferedImage resizeImage(BufferedImage originalImage, double scale) {
        int newWidth = (int) (originalImage.getWidth() * scale);
        int newHeight = (int) (originalImage.getHeight() * scale);
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType());
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return resizedImage;
    }

    private static String getImageFileType(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
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
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public enum CompressionResult {
        COMPRESSED_SUCCESS("成功壓縮"),
        SKIPPED_CONDITION_NOT_MET("不符條件跳過"),
        SKIPPED_NOT_FOUND("來源檔案不存在"),
        FAILED_COMPRESSION("壓縮失敗"),
        FAILED_IO_ERROR("IO錯誤"),
        FAILED_OUT_OF_MEMORY("記憶體溢位"),
        FAILED_UNKNOWN("未知錯誤");

        private final String description;
        CompressionResult(String description) { this.description = description; }
        public String getDescription() { return description; }
    }
}