package work.pollochang.compression.image.core;

public enum CompressionResult {
    COMPRESSED_SUCCESS("成功壓縮"),
    SKIPPED_CONDITION_NOT_MET("不符條件跳過"),
    SKIPPED_NOT_FOUND("來源檔案不存在"),
    FAILED_COMPRESSION("壓縮失敗(無法達標)"),
    FAILED_UNSUPPORTED_FORMAT("格式不支援"),
    FAILED_IO_ERROR("IO錯誤"),
    FAILED_OUT_OF_MEMORY("記憶體溢位"),
    FAILED_UNKNOWN("未知錯誤");

    private final String description;
    CompressionResult(String description) { this.description = description; }
    public String getDescription() { return description; }
}
