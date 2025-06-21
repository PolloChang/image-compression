package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileTools {

    /**
     * 檢查目標路徑存在
     * @param fileName
     */
    public void ensureDirectoryExists(String fileName) {
        // 1. 使用 Path 的 resolve() 方法安全地組合路徑
        Path goalFilePath = Paths.get(fileName);

        // 2. 取得父目錄的 Path 物件 (即 SAVE_DIR)
        Path parentDirPath = goalFilePath.getParent(); // 或者直接使用 Paths.get(SAVE_DIR) 也可以

        // 3. 檢查父目錄是否存在
        if (!Files.exists(parentDirPath)) {
            // 如果目錄不存在，則建立它 (包括所有不存在的父級目錄)
            try {
                Files.createDirectories(parentDirPath);
                log.info("目標目錄已建立: {}", parentDirPath);
            } catch (IOException e) {
                throw new RuntimeException("無法建立目錄", e);
            }
        } else {
            log.debug("目標目錄已存在: {}", parentDirPath);
        }

        // 4. 當目錄確定存在後，您可以繼續處理檔案寫入等操作
        log.info("目標檔案完整路徑為: {}", goalFilePath);
        // 例如：Files.copy(sourcePath, goalFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

}
