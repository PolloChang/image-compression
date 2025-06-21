package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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

    @Option(names = {"-h", "--minHeight"}, defaultValue = "1920", description = "限制要壓縮的圖片高 (預設: 1920)。")
    private int minHeight;

    @Override
    public Integer call() throws Exception {
        log.info("壓縮任務開始");
        log.info("來源列表: {}", fileList.getAbsolutePath());
        log.info("輸出目錄: {}", saveDir.getAbsolutePath());
        log.info("壓縮品質: {}", quality);
        log.info("限制要壓縮的圖片大小: {}", minSizeBytes);
        log.info("限制要壓縮的圖片長: {}", minWidth);
        log.info("限制要壓縮的圖片高: {}", minHeight);


        CompressionBatch compressionBatch = new CompressionBatch();
        compressionBatch.setQuality(quality);
        compressionBatch.setSaveDir(saveDir.getAbsolutePath());
        compressionBatch.setFileListPath(fileList.getAbsolutePath());
        compressionBatch.setMinSizeBytes(minSizeBytes);
        compressionBatch.setMinWidth(minWidth);
        compressionBatch.setMinHeight(minHeight);
        compressionBatch.execute();

        log.info("所有任務執行完畢");
        return 0; // 成功時返回 0
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Execute()).execute(args);
        System.exit(exitCode);
    }
}