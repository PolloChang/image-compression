package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Execute {

    public static void main(String[] args) {


        String fileListPath = "/data/tmp/resize-image/file-list.txt"; //arg[0]
        String saveDir = "/data/tmp/resize-image/result";

        // 設定壓縮品質，範圍從 0.0 (最低品質，檔案最小) 到 1.0 (最高品質，檔案最大)
        float quality = 0.25f;

        log.info("壓縮開始");

        CompressionBatch compressionBatch = new CompressionBatch();
        compressionBatch.setQuality(quality);
        compressionBatch.setSaveDir(saveDir);
        compressionBatch.setFileListPath(fileListPath);
        compressionBatch.execute();

        log.info("壓縮完成");

    }

}

