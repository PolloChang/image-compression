package work.pollochang.compression.image.report;

public record CompressionParams(float quality, long minSizeBytes, int minWidth, int minHeight, long targetMaxSizeBytes) {}