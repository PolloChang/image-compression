package work.pollochang.compression.image.tools;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;

@Slf4j
public class FileTools {

    /**
     * 確保指定的目錄存在，如果不存在則建立它。
     * @param directoryPath 要檢查或建立的目錄路徑
     */
    public static void ensureDirectoryExists(Path directoryPath) {
        if (!Files.exists(directoryPath)) {
            try {
                Files.createDirectories(directoryPath);
                log.info("{} - 目標目錄已建立", directoryPath);
            } catch (IOException e) {
                // 拋出 RuntimeException 使上層能夠捕獲並中止程式
                throw new RuntimeException("無法建立目錄: " + directoryPath, e);
            }
        } else {
            log.debug("{} - 目標目錄已存在", directoryPath);
        }
    }

    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

}