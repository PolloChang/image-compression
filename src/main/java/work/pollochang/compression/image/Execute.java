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

    @Override
    public Integer call() throws Exception {
        log.info("壓縮任務開始");
        log.info("來源列表: {}", fileList.getAbsolutePath());
        log.info("輸出目錄: {}", saveDir.getAbsolutePath());
        log.info("壓縮品質: {}", quality);

        CompressionBatch compressionBatch = new CompressionBatch();
        compressionBatch.setQuality(quality);
        compressionBatch.setSaveDir(saveDir.getAbsolutePath());
        compressionBatch.setFileListPath(fileList.getAbsolutePath());
        compressionBatch.execute();

        log.info("所有任務執行完畢");
        return 0; // 成功時返回 0
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Execute()).execute(args);
        System.exit(exitCode);
    }
}