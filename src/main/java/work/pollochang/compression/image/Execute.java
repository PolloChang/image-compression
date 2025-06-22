package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import work.pollochang.compression.image.report.CompressionParams;
import work.pollochang.compression.image.tools.FileTools;

import java.io.File;
import java.util.concurrent.Callable;

@Slf4j
@Command(name = "image-compressor",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "批次圖片壓縮工具")
public class Execute implements Callable<Integer> {

    @Option(names = {"-f", "--file-list"}, required = true, description = "包含圖片路徑的文字檔案。")
    private File fileList;

    @Option(names = {"-o", "--output-dir"}, required = true, description = "壓縮後圖片的儲存目錄。")
    private File saveDir;

    @Option(names = {"-q", "--quality"}, defaultValue = "0.25", description = "壓縮品質，範圍從 0.0 (最低品質，檔案最小) 到 1.0 (最高品質，檔案最大) (預設: 0.25)。")
    private float quality;

    @Option(names = {"-s", "--minSize"}, defaultValue = "1048576", description = "限制要壓縮的圖片大小 (預設: 1048576 (1MB))。")
    private long minSizeBytes;

    @Option(names = {"-w", "--minWidth"}, defaultValue = "1920", description = "限制要壓縮的圖片長 (預設: 1920)。")
    private int minWidth;

    @Option(names = {"-i", "--minHeight"}, defaultValue = "1920", description = "限制要壓縮的圖片高 (預設: 1920)。")
    private int minHeight;

    @Option(names = {"-t", "--target-max-size"}, defaultValue = "1048576", description = "壓縮後單一檔案的目標大小上限(bytes) (預設: 1048576, 即 1MB)。")
    private long targetMaxSizeBytes;

    @Option(names = {"--timeOut"}, defaultValue = "24", description = "設定執行時間超時(小時) (預設: 24 小時)。")
    private long timeOutHr;

    // 【新增】加入 H2 資料庫路徑的新選項
    @Option(names = {"--cache-db"}, defaultValue = "image-compression-cache", description = "H2 學習快取資料庫的檔案路徑。")
    private File h2DbFile;

    @Override
    public Integer call() throws Exception {

        log.info("========================================壓縮程式參數設定========================================");
        log.info("壓縮任務開始");
        log.info("來源列表: {}", fileList.getAbsolutePath());
        log.info("輸出目錄: {}", saveDir.getAbsolutePath());
        log.info("JPG 壓縮品質: {}", quality);
        log.info("最小壓縮尺寸: {}x{}", minWidth, minHeight);
        log.info("最小壓縮大小: {}", FileTools.formatFileSize(minSizeBytes));
        log.info("目標檔案大小上限: {}", FileTools.formatFileSize(targetMaxSizeBytes));
        log.info("設定超時執行時間: {} 小時", timeOutHr);
        log.info("學習快取資料庫: {}", h2DbFile.getAbsolutePath());
        log.info("========================================壓縮程式參數設定========================================");


        // 使用 record 封裝參數
        CompressionParams params = new CompressionParams(
                quality,
                minSizeBytes,
                minWidth,
                minHeight,
                targetMaxSizeBytes
        );

        CompressionBatch compressionBatch = new CompressionBatch();
        compressionBatch.setFileListPath(fileList.getAbsolutePath());
        compressionBatch.setSaveDir(saveDir.getAbsolutePath());
        compressionBatch.setCompressionParams(params);
        compressionBatch.setTimeOutHr(timeOutHr);
        compressionBatch.setH2CachePath(h2DbFile.toPath());
        compressionBatch.execute();

        log.info("所有任務執行完畢");
        return 0; // 成功時返回 0
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Execute()).execute(args);
        System.exit(exitCode);
    }
}