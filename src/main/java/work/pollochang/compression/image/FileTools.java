package work.pollochang.compression.image;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class FileTools {

    /**
     * 確保指定的目錄存在，如果不存在則建立它。
     * @param directoryPath 要檢查或建立的目錄路徑
     */
    public void ensureDirectoryExists(Path directoryPath) {
        if (!Files.exists(directoryPath)) {
            try {
                Files.createDirectories(directoryPath);
                log.info("目標目錄已建立: {}", directoryPath);
            } catch (IOException e) {
                // 拋出 RuntimeException 使上層能夠捕獲並中止程式
                throw new RuntimeException("無法建立目錄: " + directoryPath, e);
            }
        } else {
            log.debug("目標目錄已存在: {}", directoryPath);
        }
    }
}