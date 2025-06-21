package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Execute {

    public static void main(String[] args) {


        String FilePath = "/data/tmp/resize-image/file-list.txt" ; //arg[0]
        String SAVE_DIR = "/data/tmp/resize-image/result" ;

        // 設定壓縮品質，範圍從 0.0 (最低品質，檔案最小) 到 1.0 (最高品質，檔案最大)
        float COMPRESSION_QUALITY = 0.5f;


        // 從檔案取得 圖片絕對路徑
        List<String> filePathArray = new ArrayList<>(); // 使用 ArrayList 彈性較大

        try (BufferedReader br = new BufferedReader(new FileReader(FilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                filePathArray.add(line); // 將每一行加入列表中
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        FileTools fileTools = new FileTools();

        for (String absolutePath : filePathArray) {
            Path path = Paths.get(absolutePath);
            Path fileNamePath = path.getFileName();
            String fileName = fileNamePath.toString(); // 轉換為 String

            log.debug("原始路徑: " + absolutePath);
            log.debug("取得的檔名: " + fileName);

            if (!Files.exists(path)) {
                log.info("檔案不存在: " + absolutePath);
                continue;
            }

            log.debug("繼續執行");

            String goalFilePath = SAVE_DIR+"/"+fileName;

            Path parentDirPath = Paths.get(SAVE_DIR);

            fileTools.ensureDirectoryExists(goalFilePath);

            File inputFile = new File(absolutePath);
            File outputFile = new File(goalFilePath);

            ImageCompression imageCompression = new ImageCompression();
            try {

                imageCompression.compressImage(inputFile, outputFile, COMPRESSION_QUALITY);

            } catch (IOException e) {
                log.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                log.error("參數錯誤: " + e.getMessage());
            }


        }

    }

}

