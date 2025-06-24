package work.pollochang.compression.image.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.pollochang.compression.image.report.CompressionParams;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageCompressionPngTest {

    /**
     * 產生測試用影像
     */
    private BufferedImage createTestImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 原圖比目標小（應該直接儲存）
     * @param tempDir
     * @throws IOException
     */
    @Test
    void testImageSmallerThanTarget_ShouldSaveDirectly(@TempDir Path tempDir) throws IOException {
        BufferedImage img = createTestImage(100, 100, Color.RED);
        Path output = tempDir.resolve("small.png");

        boolean result = ImageCompressionPng.compressPngWithTargetSize(img, output,
                new CompressionParams(0, 0,100,100,0)); // target larger than image

        assertFalse(result);
    }

    /**
     * 原圖比目標大（應該縮小）
     * @param tempDir
     * @throws IOException
     */
    @Test
    void testImageLargerThanTarget_ShouldResize(@TempDir Path tempDir) throws IOException {
        BufferedImage img = createTestImage(800, 600, Color.BLUE);
        Path output = tempDir.resolve("resized.png");

        boolean result = ImageCompressionPng.compressPngWithTargetSize(img, output,
                new CompressionParams(0, 0,100,100,0)); // target smaller than image


        assertTrue(result);
        assertTrue(Files.exists(output));
        BufferedImage resized = ImageIO.read(output.toFile());

        boolean check = (resized.getWidth() <= 100 || resized.getHeight() <= 100);
        assertTrue(check);

    }

    /**
     * 傳入 null 參數（應觸發 NPE）
     * @param tempDir
     */
    @Test
    void testNullInputs_ShouldThrowNullPointerException(@TempDir Path tempDir) {
        BufferedImage dummy = createTestImage(100, 100, Color.BLACK);
        Path output = tempDir.resolve("null.png");
        CompressionParams params = new CompressionParams(0, 0,100,100,0);

        assertThrows(NullPointerException.class,
                () -> ImageCompressionPng.compressPngWithTargetSize(null, output, params));
        assertThrows(NullPointerException.class,
                () -> ImageCompressionPng.compressPngWithTargetSize(dummy, null, params));
        assertThrows(NullPointerException.class,
                () -> ImageCompressionPng.compressPngWithTargetSize(dummy, output, null));
    }
}
