package work.pollochang.compression.image;

/**
 * 用於快取的 Key，代表圖片的特徵「桶」。
 * @param widthBucket 寬度所在的桶 (e.g., 每 100px 一個桶)
 * @param heightBucket 高度所在的桶 (e.g., 每 100px 一個桶)
 * @param sizeBucket 檔案大小所在的桶 (e.g., 每 100KB 一個桶)
 */
public record SimilarityKey(int widthBucket, int heightBucket, long sizeBucket) {}