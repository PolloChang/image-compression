package work.pollochang.compression.image.learn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.learn.jpg.SimilarityKey;
import work.pollochang.compression.image.learn.jpg.SimilarityKeyDeserializer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class Learn {
    /**
     * 從指定的 JSON 檔案讀取並還原快取。
     * @param path 快取檔案的路徑
     * @return 一個 ConcurrentHashMap，如果檔案不存在或讀取失敗則為空。
     */
    public Map<SimilarityKey, LearnedParams> loadCacheFromFile(Path path) {
        if (Files.exists(path)) {
            ObjectMapper mapper = new ObjectMapper();

            // 1. 建立一個模組
            SimpleModule module = new SimpleModule();
            // 2. 告訴模組，當遇到需要將 key 轉成 SimilarityKey 的情況時，請使用我們自訂的 Deserializer
            module.addKeyDeserializer(SimilarityKey.class, new SimilarityKeyDeserializer());
            // 3. 將這個模組註冊到 ObjectMapper 中
            mapper.registerModule(module);

            try {
                // 使用 TypeReference 來讓 Jackson 知道要轉換成的複雜 Map 型別
                Map<SimilarityKey, LearnedParams> loadedMap = mapper.readValue(path.toFile(), new TypeReference<>() {});
                log.info("成功從 {} 讀取 {} 筆學習快取紀錄。", path, loadedMap.size());
                // 返回一個 ConcurrentHashMap 以確保執行緒安全
                return new ConcurrentHashMap<>(loadedMap);
            } catch (IOException e) {
                log.warn("讀取快取檔案 {} 失敗，將使用新的空快取。", path, e);
            }
        } else {
            log.info("快取檔案 {} 不存在，將建立新的空快取。", path);
        }
        return new ConcurrentHashMap<>();
    }

    /**
     * 將記憶體中的快取儲存到指定的 JSON 檔案。
     * @param path 快取檔案的路徑
     * @param cache 要儲存的快取 Map
     */
    public void saveCacheToFile(Path path, Map<SimilarityKey, LearnedParams> cache) {
        if (cache == null || cache.isEmpty()) {
            log.info("快取為空，無需儲存。");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        try {
            log.info("正在將 {} 筆快取紀錄儲存至 {} ...", cache.size(), path);
            mapper.writeValue(path.toFile(), cache);
            log.info("快取成功儲存。");
        } catch (IOException e) {
            log.error("儲存快取至檔案 {} 時發生錯誤。", path, e);
        }
    }

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
