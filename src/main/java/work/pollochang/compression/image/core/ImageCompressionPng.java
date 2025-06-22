package work.pollochang.compression.image.core;

import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.report.CompressionParams;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static work.pollochang.compression.image.tools.ImageTools.resizeImage;

@Slf4j
public class ImageCompressionPng {
    /**
     * 處理 PNG 檔案。如果圖片尺寸超過限制，則按比例縮小至目標尺寸內，然後儲存。
     * 只處理圖片尺寸
     * @param originalImage 原始 BufferedImage 物件
     * @param outputFile 壓縮後要儲存的路徑
     * @param params 壓縮參數，主要使用 minWidth 和 minHeight
     * @return 是否寫入成功
     * @throws IOException IO 錯誤
     */
    public static boolean compressPngWithTargetSize(BufferedImage originalImage, Path outputFile, CompressionParams params) throws IOException {
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
}
