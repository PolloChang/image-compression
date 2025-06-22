package work.pollochang.compression.image.tools;

import work.pollochang.compression.image.learn.jpg.SimilarityKey;

import java.awt.image.BufferedImage;

public class CacheTools {
    /**
     * 輔助方法來產生 Key
     * @param image
     * @param fileSize
     * @return
     */
    public static SimilarityKey createKey(BufferedImage image, long fileSize) {
        // 調整分桶大小可以改變快取的粒度
        // 寬/高每 100px 一個桶，檔案大小每 100KB (102400 bytes) 一個桶
        int widthBucket = image.getWidth() / 100;
        int heightBucket = image.getHeight() / 100;
        long sizeBucket = fileSize / 102400;
        return new SimilarityKey(widthBucket, heightBucket, sizeBucket);
    }
}
