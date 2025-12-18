package com.ray.atten.desktop.utils;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageConverter {

    // 正则表达式用于匹配并去除 Data URI 头部
    private static final Pattern DATA_URI_PATTERN =
            Pattern.compile("^data:image/[a-zA-Z]+;base64,");

    /**
     * 将 Base64 字符串转换为 JavaFX Image 对象。
     *
     * @param base64String 包含图片数据的 Base64 字符串。
     * @return JavaFX Image 对象，如果失败则返回 null 或抛出异常。
     */
    public static Image base64ToImage(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }

        // 1. 预处理：去除 Data URI 头部（如果有）
        // 例如：将 "data:image/png;base64,iVBORw0KGgo..." 变为 "iVBORw0KGgo..."
        Matcher matcher = DATA_URI_PATTERN.matcher(base64String);
        String cleanedBase64 = matcher.replaceAll("");

        System.out.println("Base64 字符串长度: " + cleanedBase64.length());
        try {
            // 2. 解码 Base64 字符串为原始字节数组
            // 注意：要使用 Base64.getDecoder()
            byte[] imageBytes = Base64.getDecoder().decode(cleanedBase64);

            // 关键步骤：检查字节数组是否为空
            if (imageBytes.length == 0) {
                System.err.println("❌ 解码后的字节数组长度为 0，请检查 Base64 字符串是否为空或无效。");
                return null; // 解码失败，无法继续
            }

            // 3. 将字节数组转换为输入流
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);

            Image image = new Image(bis);
            // 4. 使用 InputStream 创建 JavaFX Image 对象
            // 这种方式是 JavaFX 加载二进制图片数据流的标准方法。
            if (image.isError()) {
                System.err.println("【❌ 最终错误】JavaFX Image 加载错误，原因: " + image.getException().getMessage());
                // 如果能到这里，且文件测试成功，请确认图片格式（如非标准 BMP、TIFF 等）
            } else {
                System.out.println("【✅ 成功】Image 对象创建成功!");
            }
            return image;

        } catch (IllegalArgumentException e) {
            // Base64 解码失败（字符串格式不正确）
            System.err.println("Base64 string decoding failed: " + e.getMessage());
            return null;
        } catch (Exception e) {
            // 其他 IO 或 Image 加载错误
            System.err.println("Failed to load image from Base64: " + e.getMessage());
            // 抛出或返回 null，便于上层处理
            return null;
        }
    }

    /**
     * 辅助方法：缩放图片
     */
    public static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        // 使用更高质量的缩放算法
        java.awt.Image temp = originalImage.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(temp, 0, 0, null);
        g2d.dispose();

        // 质量压缩通常在保存为 JPEG 时实现，这里主要完成尺寸压缩
        return resizedImage;
    }

    /**
     * 辅助方法：编码为 Base64
     */
    public static String encodeImageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            // 写入到 ByteArrayOutputStream，使用 JPEG 格式并进行质量压缩
            ImageIO.write(image, "jpg", os);
            return Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
