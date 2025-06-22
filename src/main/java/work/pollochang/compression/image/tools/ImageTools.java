package work.pollochang.compression.image.tools;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageTools {
    public static BufferedImage resizeImage(BufferedImage originalImage, double scale) {
        int newWidth = Math.max(1, (int) (originalImage.getWidth() * scale));
        int newHeight = Math.max(1, (int) (originalImage.getHeight() * scale));

        // 保留 Alpha 通道
        int imageType = originalImage.getType();
        if (imageType == 0 || imageType == BufferedImage.TYPE_CUSTOM) {
            imageType = originalImage.getAlphaRaster() != null ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        }

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D g2d = resizedImage.createGraphics();
        // 使用更高品質的縮放演算法
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return resizedImage;
    }
}
