package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
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
     * @param inputPath   來源圖片檔案
     * @param outputDir  輸出圖片檔案
     * @param quality     壓縮品質 (0.0f 到 1.0f 之間)
     */
    public boolean processImage(Path inputPath, Path outputDir, float quality) {

        boolean isCompressed = false;

        if (!Files.exists(inputPath)) {
            log.warn("檔案不存在，跳過處理: {}", inputPath);
            return isCompressed;
        }

        String fileName = inputPath.getFileName().toString();
        File outputFile = outputDir.resolve(fileName).toFile();
        File inputFile = inputPath.toFile();

        try {

            log.info("開始壓縮檔案: {} ", inputPath);

            isCompressed = compressImage(inputFile, outputFile, quality);

            log.info("圖片已成功壓縮並儲存: {} -> {}", inputPath, outputFile.getAbsolutePath());

        } catch (IOException e) {
            log.warn("無法處理圖片檔案 (可能非支援格式或檔案已損毀): {}", inputPath, e);
        } catch (Exception e) {
            log.error("處理檔案時發生未知錯誤: {}", inputPath, e);
        } finally {
            return isCompressed;
        }
    }

    /**
     * 壓縮圖片檔案
     *
     * @param inputFile   來源圖片檔案
     * @param outputFile  輸出圖片檔案
     * @param quality     壓縮品質 (0.0f 到 1.0f 之間)
     * @throws IOException 檔案讀寫發生錯誤時拋出
     * @throws IllegalArgumentException 壓縮品質參數錯誤時拋出
     */
    private boolean compressImage(File inputFile, File outputFile, float quality) throws IOException {


        try {
            String fileType = getImageFileType(inputFile);
            // 1. 讀取來源圖片
            BufferedImage image = ImageIO.read(inputFile);
            log.debug("檔案類型: {}", fileType);

            switch (fileType) {
                case "jpg":
                    compressImageJPG(image, outputFile, quality);
                    return true;
                case "png":
                    resizeImagePNG(image, outputFile);
                    return true;
                default:
                    log.warn("不支援的檔案格式: {}", inputFile);
                    return false;
            }
        } catch (Exception e) {
            log.error("處理檔案時發生錯誤: {}", inputFile, e);
            return false;
        }

    }

    private void resizeImagePNG(BufferedImage originalImage, File outputFile) throws IOException {

        log.debug("進行壓縮: PNG");

        try {

            // 定義目標寬度和高度
            int targetWidth = 1920; // 例如，縮小到 1920 像素寬
            int targetHeight = (int) (originalImage.getHeight() * ((double) targetWidth / originalImage.getWidth())); // 等比例縮放

            // 創建一個新的 BufferedImage，用於存放縮放後的圖片
            // 建議使用原始圖片的類型，或者 BufferedImage.TYPE_INT_ARGB 以保留透明度
            BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, originalImage.getType());

            // 取得 Graphics2D 物件以進行繪圖
            Graphics2D g2d = resizedImage.createGraphics();

            // 設定渲染提示，提高縮放品質 (例如抗鋸齒和平滑度)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 將原始圖片繪製到新的 BufferedImage 上，並指定目標尺寸
            g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose(); // 釋放繪圖資源

            // 輸出縮放後的圖片
            ImageIO.write(resizedImage, "png", outputFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void compressImageJPG(BufferedImage image, File outputFile, float quality) throws IOException {
        log.debug("進行壓縮: JPG");

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

    /**
     * 取得檔案格式
     * @param file
     * @return
     * @throws IOException
     */
    private String getImageFileType(File file) throws IOException {

        if(file == null || !file.exists() || !file.isFile()) {
            log.warn("無法讀取圖片檔案，可能非支援格式或檔案已損毀: {}", file.getAbsolutePath());
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magicBytes = new byte[8]; // PNG 需要8個位元組，JPG 只需要4個
            int bytesRead = fis.read(magicBytes, 0, Math.min(magicBytes.length, (int)file.length()));

            if (bytesRead >= 8) {
                // 檢查 PNG
                if (magicBytes[0] == (byte) 0x89 && magicBytes[1] == (byte) 0x50 &&
                        magicBytes[2] == (byte) 0x4E && magicBytes[3] == (byte) 0x47 &&
                        magicBytes[4] == (byte) 0x0D && magicBytes[5] == (byte) 0x0A &&
                        magicBytes[6] == (byte) 0x1A && magicBytes[7] == (byte) 0x0A) {
                    return "png";
                }
            }

            if (bytesRead >= 4) {
                // 檢查 JPG/JPEG
                if (magicBytes[0] == (byte) 0xFF && magicBytes[1] == (byte) 0xD8 &&
                        magicBytes[2] == (byte) 0xFF &&
                        (magicBytes[3] == (byte) 0xE0 || magicBytes[3] == (byte) 0xE1 || magicBytes[3] == (byte) 0xE8)) {
                    return "jpg";
                }
            }
        }
        return "UNKNOWN";
    }
}