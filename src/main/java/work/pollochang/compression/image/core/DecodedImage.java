package work.pollochang.compression.image.core;

import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;

// 內部類，用於封裝解碼後的圖片和其讀取器，方便資源管理
public record DecodedImage(BufferedImage image, ImageReader reader) implements AutoCloseable {
    @Override
    public void close() {
        if (image != null) {
            image.flush();
        }
        if (reader != null) {
            reader.dispose();
        }
    }
}
