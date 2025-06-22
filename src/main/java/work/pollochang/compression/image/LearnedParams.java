package work.pollochang.compression.image;

/**
 * 用於快取的 Value，儲存學到的最佳壓縮參數。
 * @param quality 成功壓縮的品質
 * @param scale 成功壓縮的縮放比例
 */
public record LearnedParams(float quality, double scale) {}