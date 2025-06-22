package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.*;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Iterator;

/**
 * 圖片壓縮工具類
 */
@Slf4j
public final class ImageCompression {

    // 註冊 ImageIO 外掛程式，禁用磁碟快取，強制使用記憶體操作，避免 I/O 瓶頸。
    static {
        IIORegistry.getDefaultInstance().registerApplicationClasspathSpis();
        ImageIO.setUseCache(false);
    }

    public record CompressionParams(float quality, long minSizeBytes, int minWidth, int minHeight, long targetMaxSizeBytes) {}
    public record CompressionReport(CompressionResult result, long originalSize, long compressedSize) {}

    // 內部類，用於封裝解碼後的圖片和其讀取器，方便資源管理
    private record DecodedImage(BufferedImage image, ImageReader reader) implements AutoCloseable {
        @Override
        public void close() {
            if (image != null) {
                image.flush();
            }
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private ImageCompression() {}

    public static CompressionReport processImage(Path inputPath, Path outputDir, CompressionParams params) {
        long originalSize;
        try {
            if (!Files.exists(inputPath) || !Files.isReadable(inputPath)) {
                log.warn("檔案不存在或不可讀，跳過: {}", inputPath);
                return new CompressionReport(CompressionResult.SKIPPED_NOT_FOUND, 0, 0);
            }
            originalSize = Files.size(inputPath);
        } catch (IOException e) {
            log.warn("無法讀取檔案大小: {}", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_IO_ERROR, 0, 0);
        }

        try (DecodedImage decodedImage = decodeImageWithSubsampling(inputPath, params, originalSize)) {
            if (decodedImage == null) {
                // 如果解碼階段就已決定跳過或失敗，會回傳 null
                // 檢查是否符合壓縮條件的日誌已在 decodeImageWithSubsampling 內部處理
                boolean shouldCompress = (originalSize > params.minSizeBytes()); // 簡化判斷，詳細尺寸判斷已在內部
                return new CompressionReport(
                        shouldCompress ? CompressionResult.FAILED_UNSUPPORTED_FORMAT : CompressionResult.SKIPPED_CONDITION_NOT_MET,
                        originalSize, originalSize
                );
            }

            Path outputFile = outputDir.resolve(inputPath.getFileName());
            log.info("開始處理: {}", inputPath);

            boolean success = compressImageIteratively(decodedImage, outputFile, params);

            if (success) {
                long compressedSize = Files.size(outputFile);
                double ratio = 100.0 * (originalSize - compressedSize) / originalSize;
                log.info("處理成功: {} -> {} (大小: {} -> {}, 節省: {}%)",
                        inputPath.getFileName(), outputFile.getFileName(),
                        formatFileSize(originalSize), formatFileSize(compressedSize), ratio);
                return new CompressionReport(CompressionResult.COMPRESSED_SUCCESS, originalSize, compressedSize);
            } else {
                log.warn("無法在目標大小限制下完成壓縮: {}", inputPath);
                // 即使壓縮失敗，也應該清除可能已建立的空檔案或不完整檔案
                Files.deleteIfExists(outputFile);
                return new CompressionReport(CompressionResult.FAILED_COMPRESSION, originalSize, 0);
            }
        } catch (IOException e) {
            log.warn("處理圖片時發生 I/O 錯誤 (可能非支援格式或檔案損毀): {}", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_IO_ERROR, originalSize, 0);
        } catch (OutOfMemoryError e) {
            // 儘管已經做了二次取樣，極端情況下仍可能發生。
            log.error("處理檔案時發生記憶體溢位錯誤 (圖片可能過大或格式有問題): {}", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_OUT_OF_MEMORY, originalSize, 0);
        } catch (Exception e) {
            log.error("處理檔案時發生未知錯誤: {}", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_UNKNOWN, originalSize, 0);
        }
    }

    private static DecodedImage decodeImageWithSubsampling(Path inputPath, CompressionParams params, long fileSize) throws IOException {
        if (fileSize <= params.minSizeBytes()) {
            log.info("檔案大小 {} 未超過最小壓縮門檻 {}，跳過: {}", formatFileSize(fileSize), formatFileSize(params.minSizeBytes()), inputPath);
            return null;
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(Files.newInputStream(inputPath))) {
            if (in == null) {
                log.warn("無法建立圖片輸入流，跳過: {}", inputPath);
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                log.warn("找不到對應的圖片讀取器，跳過: {}", inputPath);
                return null;
            }

            ImageReader reader = readers.next();
            reader.setInput(in, true, true);

            try {
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= params.minWidth() || height <= params.minHeight()) {
                    log.info("圖片尺寸 {}x{} 未超過最小壓縮門檻 {}x{}，跳過: {}", width, height, params.minWidth(), params.minHeight(), inputPath);
                    reader.dispose();
                    return null;
                }

                ImageReadParam param = reader.getDefaultReadParam();
                // 核心優化：計算取樣率以降低記憶體使用
                // 目標是讓圖片最長邊接近 4K (4096px) 進行初步讀取，可根據實際情況調整
                int subsampling = 1;
                int maxDim = Math.max(width, height);
                final int PREFERRED_MAX_DIM = 4096;
                if (maxDim > PREFERRED_MAX_DIM) {
                    subsampling = (int) Math.floor((double) maxDim / PREFERRED_MAX_DIM);
                }

                // ImageIO 的 subsampling 只支援整數，並且對於某些格式(如隔行掃描的 JPG)有特定要求
                if (subsampling > 1) {
                    // 確保取樣率是 2 的冪，對某些 JPG 解碼器更友好
                    subsampling = Integer.highestOneBit(subsampling);
                    log.debug("對圖片 {} 應用二次取樣，比率: {}", inputPath.getFileName(), subsampling);
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }

                BufferedImage image = reader.read(0, param);
                // 注意：此時返回的 reader 不能關閉，因為 DecodedImage 的 AutoCloseable 會負責關閉
                return new DecodedImage(image, reader);

            } catch (Exception e) {
                // 如果在讀取尺寸或應用取樣時出錯，安全地釋放 reader
                reader.dispose();
                throw e; // 重新拋出異常，讓外層捕捉
            }
        }
    }

    private static boolean compressImageIteratively(DecodedImage decodedImage, Path outputFile, CompressionParams params) throws IOException {
        ImageReaderSpi spi = decodedImage.reader().getOriginatingProvider();
        String formatName = spi.getFormatNames()[0].toLowerCase();

        BufferedImage initialImage = decodedImage.image();

        switch (formatName) {
            case "jpeg":
            case "jpg":
                return compressJpgWithTargetSize(initialImage, outputFile, params);
            case "png":
                return compressPngWithTargetSize(initialImage, outputFile, params);
            default:
                log.warn("不支援的檔案格式: {} (來自 SPI)，跳過壓縮: {}", formatName, outputFile.getFileName());
                return false;
        }

    }

    private static boolean compressJpgWithTargetSize(BufferedImage originalImage, Path outputFile, CompressionParams params) throws IOException {
        final float QUALITY_STEP = 0.1f;
        final double SCALE_STEP = 0.85;

        BufferedImage currentImage = originalImage;
        // 為了避免重複創建和銷毀，只在需要時才縮放
        boolean isOriginal = true;

        try {
            // 從目前的圖片尺寸開始，由高品質往低品質嘗試
            for (float quality = params.quality(); quality >= 0.1f; quality -= QUALITY_STEP) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    compressJpgToStream(currentImage, bos, quality);
                    if (bos.size() <= params.targetMaxSizeBytes()) {
                        log.debug("找到合適參數: quality={}, scale={}", String.format("%.2f", quality), "1.0 (current size)");
                        Files.write(outputFile, bos.toByteArray());
                        return true;
                    }
                }
            }

            // 如果最高品質的嘗試仍然失敗，開始縮放圖片
            // 外部迴圈改為縮放，內部迴圈調整品質
            for (double scale = SCALE_STEP; scale > 0.1; scale *= SCALE_STEP) {

                // 釋放上一輪的縮放圖
                if (!isOriginal) {
                    currentImage.flush();
                }
                currentImage = resizeImage(originalImage, scale);
                isOriginal = false; // 標記 currentImage 已經是一個縮放後的副本

                log.debug("檔案仍然過大，縮放至 {}%", (int) (scale * 100));

                // 在新的尺寸下，再次從高到低嘗試品質
                for (float quality = params.quality(); quality >= 0.1f; quality -= QUALITY_STEP) {
                    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        compressJpgToStream(currentImage, bos, quality);
                        if (bos.size() <= params.targetMaxSizeBytes()) {
                            log.debug("找到合適參數: quality={}, scale={}", String.format("%.2f", quality), String.format("%.2f", scale));
                            Files.write(outputFile, bos.toByteArray());
                            return true;
                        }
                    }
                }
            }
        } finally {
            // 只有當 currentImage 不是原始圖片時才 flush，避免重複 flush
            if (!isOriginal && currentImage != null) {
                currentImage.flush();
            }
        }

        log.warn("無法在目標大小限制下完成壓縮: {}", outputFile.getFileName());
        return false;
    }

    private static void compressJpgToStream(BufferedImage image, OutputStream os, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
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

    /**
     * 處理 PNG 檔案。如果圖片尺寸超過限制，則按比例縮小至目標尺寸內，然後儲存。
     * 只處理圖片尺寸
     * @param originalImage 原始 BufferedImage 物件
     * @param outputFile 壓縮後要儲存的路徑
     * @param params 壓縮參數，主要使用 minWidth 和 minHeight
     * @return 是否寫入成功
     * @throws IOException IO 錯誤
     */
    private static boolean compressPngWithTargetSize(BufferedImage originalImage, Path outputFile, CompressionParams params) throws IOException {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        int targetWidth = params.minWidth();
        int targetHeight = params.minHeight();

        // 檢查圖片是否需要縮小
        if (originalWidth <= targetWidth && originalHeight <= targetHeight) {
            log.info("PNG 圖片尺寸 {}x{} 未超過目標 {}x{}，直接儲存。", originalWidth, originalHeight, targetWidth, targetHeight);
            // 如果原始尺寸已在目標範圍內，直接寫入檔案
            return ImageIO.write(originalImage, "png", outputFile.toFile());
        }

        // --- 計算縮放比例以維持長寬比 ---
        // 計算寬度和高度的縮放比例
        double widthRatio = (double) targetWidth / originalWidth;
        double heightRatio = (double) targetHeight / originalHeight;

        // 選擇較小的比例，以確保縮放後的圖片能完全放入目標框內
        double scale = Math.min(widthRatio, heightRatio);

        log.info("PNG 圖片尺寸 {}x{} 超過目標 {}x{}，將以 {} 比例縮放。", originalWidth, originalHeight, targetWidth, targetHeight, String.format("%.2f", scale));

        // 使用現有的 resizeImage 方法進行縮放
        BufferedImage resizedImage = resizeImage(originalImage, scale);

        try {
            // 將縮放後的圖片寫入檔案
            return ImageIO.write(resizedImage, "png", outputFile.toFile());
        } finally {
            // 釋放由 resizeImage 產生的新圖片資源
            resizedImage.flush();
        }
    }

    private static BufferedImage resizeImage(BufferedImage originalImage, double scale) {
        int newWidth = Math.max(1, (int) (originalImage.getWidth() * scale));
        int newHeight = Math.max(1, (int) (originalImage.getHeight() * scale));

        // 保留 Alpha 通道
        int imageType = originalImage.getType();
        if (imageType == 0 || imageType == BufferedImage.TYPE_CUSTOM) {
            imageType = originalImage.getAlphaRaster() != null ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        }

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D g2d = resizedImage.createGraphics();
        // 使用更高品質的縮放演算法
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return resizedImage;
    }

    static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }


}