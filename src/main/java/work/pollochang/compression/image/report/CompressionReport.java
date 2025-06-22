package work.pollochang.compression.image.report;

import work.pollochang.compression.image.core.CompressionResult;

public record CompressionReport(CompressionResult result, long originalSize, long compressedSize) {}
