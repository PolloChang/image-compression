package work.pollochang.compression.image;

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
import java.util.Iterator;

public class Execute {

    /**
     * 壓縮圖片檔案
     *
     * @param inputFile   來源圖片檔案
     * @param outputFile  輸出圖片檔案
     * @param quality     壓縮品質 (0.0f 到 1.0f 之間，1.0f 為最高品質)
     * @throws IOException 檔案讀寫發生錯誤時拋出
     */
    public static void compressImage(File inputFile, File outputFile, float quality) throws IOException {
        // 1. 讀取來源圖片
        BufferedImage image = ImageIO.read(inputFile);
        if (image == null) {
            System.err.println("無法讀取圖片檔案，請確認檔案格式是否支援。");
            return;
        }

        // 2. 尋找合適的 ImageWriter (這裡我們使用 JPEG，因為它支援失真壓縮)
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("找不到可用的 JPG 寫入器");
        }
        ImageWriter writer = writers.next();

        // 3. 準備輸出串流
        try (OutputStream os = new FileOutputStream(outputFile);
             ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {

            writer.setOutput(ios);

            // 4. 設定壓縮參數
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // 驗證並設定壓縮品質
                if (quality < 0.0f || quality > 1.0f) {
                    throw new IllegalArgumentException("壓縮品質必須在 0.0 到 1.0 之間");
                }
                param.setCompressionQuality(quality);
                System.out.println("設定壓縮品質為: " + quality);
            }

            // 5. 寫入壓縮後的圖片
            writer.write(null, new IIOImage(image, null, null), param);
            System.out.println("圖片已成功壓縮並儲存至: " + outputFile.getAbsolutePath());

        } finally {
            // 6. 清理資源
            writer.dispose();
        }
    }

    public static void main(String[] args) {
        // --- 請修改以下路徑 ---
        // 來源檔案：將 "path/to/your/input-image.jpg" 替換成你的原始圖片路徑
        File inputFile = new File("/data/tmp/source/test-02.jpg");

        // 輸出檔案：將 "path/to/your/output-compressed.jpg" 替換成你希望儲存的壓縮後圖片路徑
        File outputFile = new File("/data/tmp/resize/test-02.jpg");

        // 設定壓縮品質，範圍從 0.0 (最低品質，檔案最小) 到 1.0 (最高品質，檔案最大)
        float compressionQuality = 0.5f;
        // --- 路徑修改結束 ---

        // 檢查來源檔案是否存在
        if (!inputFile.exists()) {
            System.err.println("錯誤：來源檔案不存在！請檢查路徑: " + inputFile.getAbsolutePath());
            return;
        }

        // 建立輸出檔案所在的目錄 (如果不存在)
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                System.out.println("已建立輸出目錄: " + parentDir.getAbsolutePath());
            }
        }

        try {
            // 執行壓縮
            compressImage(inputFile, outputFile, compressionQuality);
        } catch (IOException e) {
            System.err.println("處理圖片時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.err.println("參數錯誤: " + e.getMessage());
        }
    }
}

