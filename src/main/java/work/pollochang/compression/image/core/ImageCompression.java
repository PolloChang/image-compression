package work.pollochang.compression.image.core;

import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.learn.LearnedParams;
import work.pollochang.compression.image.learn.jpg.SimilarityKey;
import work.pollochang.compression.image.report.CompressionReport;
import work.pollochang.compression.image.report.CompressionParams;
import work.pollochang.compression.image.tools.FileTools;


import javax.imageio.*;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import static work.pollochang.compression.image.core.ImageCompressionJpg.compressJpgWithTargetSize;
import static work.pollochang.compression.image.core.ImageCompressionPng.compressPngWithTargetSize;

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

    private ImageCompression() {}

    /**
     * 簽章
     * @param inputPath
     * @param outputDir
     * @param params
     * @param cache
     * @return
     */
    public static CompressionReport processImage(
            Path inputPath,
            Path outputDir,
            CompressionParams params,
            Map<SimilarityKey, LearnedParams> cache
    ) {
        long originalSize;
        try {
            if (!Files.exists(inputPath) || !Files.isReadable(inputPath)) {
                log.warn("{} - 檔案不存在或不可讀，跳過", inputPath);
                return new CompressionReport(CompressionResult.SKIPPED_NOT_FOUND, 0, 0);
            }
            originalSize = Files.size(inputPath);
        } catch (IOException e) {
            log.warn("{} - 無法讀取檔案大小", inputPath, e);
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
            log.debug("{} - 開始處理", inputPath);

            boolean success = compressImageIteratively(decodedImage, originalSize, outputFile, params, cache);

            if (success) {
                long compressedSize = Files.size(outputFile);
                double ratio = 100.0 * (originalSize - compressedSize) / originalSize;
                log.info("{} - 處理成功 -> {} (大小: {} -> {}, 節省: {}%)",
                        inputPath, outputFile,
                        FileTools.formatFileSize(originalSize), FileTools.formatFileSize(compressedSize), ratio);
                return new CompressionReport(CompressionResult.COMPRESSED_SUCCESS, originalSize, compressedSize);
            } else {
                log.warn("{} - 無法在目標大小限制下完成壓縮", inputPath);
                // 即使壓縮失敗，也應該清除可能已建立的空檔案或不完整檔案
                Files.deleteIfExists(outputFile);
                return new CompressionReport(CompressionResult.FAILED_COMPRESSION, originalSize, 0);
            }
        } catch (IOException e) {
            log.warn("{} - 處理圖片時發生 I/O 錯誤 (可能非支援格式或檔案損毀)", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_IO_ERROR, originalSize, 0);
        } catch (OutOfMemoryError e) {
            // 儘管已經做了二次取樣，極端情況下仍可能發生。
            log.error("{} - 處理檔案時發生記憶體溢位錯誤 (圖片可能過大或格式有問題)", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_OUT_OF_MEMORY, originalSize, 0);
        } catch (Exception e) {
            log.error("{} - 處理檔案時發生未知錯誤", inputPath, e);
            return new CompressionReport(CompressionResult.FAILED_UNKNOWN, originalSize, 0);
        }
    }

    private static DecodedImage decodeImageWithSubsampling(Path inputPath, CompressionParams params, long fileSize) throws IOException {
        if (fileSize <= params.minSizeBytes()) {
            log.info("{} - 跳過: 檔案大小 {} 未超過最小壓縮門檻 {}", inputPath, FileTools.formatFileSize(fileSize), FileTools.formatFileSize(params.minSizeBytes()));
            return null;
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(Files.newInputStream(inputPath))) {
            if (in == null) {
                log.warn("{} - 無法建立圖片輸入流，跳過", inputPath);
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                log.warn("{} - 找不到對應的圖片讀取器，跳過", inputPath);
                return null;
            }

            ImageReader reader = readers.next();
            reader.setInput(in, true, true);

            try {
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= params.minWidth() || height <= params.minHeight()) {
                    log.debug("{} - 跳過: 圖片尺寸 {}x{} 未超過最小壓縮門檻 {}x{}", inputPath, width, height, params.minWidth(), params.minHeight());
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
                    log.debug("{} - 對圖片應用二次取樣，比率: {}", inputPath.getFileName(), subsampling);
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

    private static boolean compressImageIteratively(DecodedImage decodedImage, long originalSize, Path outputFile, CompressionParams params, Map<SimilarityKey, LearnedParams> cache) throws IOException {
        ImageReaderSpi spi = decodedImage.reader().getOriginatingProvider();
        String formatName = spi.getFormatNames()[0].toLowerCase();
        BufferedImage initialImage = decodedImage.image();

        switch (formatName) {
            case "jpeg":
            case "jpg":
                // *** 傳入 originalSize 和 cache ***
                return compressJpgWithTargetSize(initialImage, originalSize, outputFile, params, cache);
            case "png":
                return compressPngWithTargetSize(initialImage, outputFile, params);
            default:
                log.warn("不支援的檔案格式: {} ...", formatName);
                return false;
        }
    }

}