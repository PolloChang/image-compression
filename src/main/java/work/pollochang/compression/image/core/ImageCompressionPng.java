package work.pollochang.compression.image.core;

import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.report.CompressionParams;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import static work.pollochang.compression.image.tools.ImageTools.resizeImage;

/**
 * 提供 PNG 圖片的壓縮工具，僅針對圖片尺寸進行調整。
 * <p>
 * 當圖片尺寸超出指定的最小寬高時，會依比例縮放圖片至指定範圍內，
 * 並保持圖片的長寬比。
 *
 * @author PolloChang
 * @since 0.1.0
 */
@Slf4j
public class ImageCompressionPng {

    /**
     * 壓縮 PNG 圖片，若圖片超過目標尺寸，則按比例縮小至指定大小。
     * 僅調整圖片尺寸，不進行影像品質壓縮。
     *
     * @param originalImage 原始 {@link BufferedImage} 圖片，不能為 null
     * @param outputFile    壓縮後圖片的輸出路徑，不能為 null
     * @param params        壓縮參數，需提供最小寬度與最小高度，不能為 null
     * @return 寫入是否成功，成功回傳 true，失敗則為 false
     * @throws IOException 若輸出過程發生錯誤，例如檔案無法寫入
     * @throws NullPointerException 若任何參數為 null
     */
    public static boolean compressPngWithTargetSize(BufferedImage originalImage, Path outputFile, CompressionParams params) throws IOException {

        Objects.requireNonNull(originalImage, "originalImage must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
        Objects.requireNonNull(params, "params must not be null");

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        int targetWidth = params.minWidth();
        int targetHeight = params.minHeight();

        // 檢查圖片是否需要縮小
        if (originalWidth <= targetWidth && originalHeight <= targetHeight) {
            log.info("PNG 圖片尺寸 {}x{} 未超過目標 {}x{}，不處理。", originalWidth, originalHeight, targetWidth, targetHeight);
            // 如果原始尺寸已在目標範圍內，不處理
            return false;
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
