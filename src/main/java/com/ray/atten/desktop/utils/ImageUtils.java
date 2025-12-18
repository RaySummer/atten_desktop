package com.ray.atten.desktop.utils;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;

public class ImageUtils {

    /**
     * 將 BufferedImage 轉換為 JavaFX Image
     */
    public static Image convertToFxImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        // 使用 SwingFXUtils.toFXImage 進行轉換
        return SwingFXUtils.toFXImage(image, null);
    }

}
