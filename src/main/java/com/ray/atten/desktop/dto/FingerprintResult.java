package com.ray.atten.desktop.dto;

import javafx.scene.image.Image;
import lombok.*;

/**
 * 封装指纹采集操作的结果，包括图像和模板数据。
 */
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FingerprintResult {

    // 指纹模板数据 (byte[])，用于比对和存储
    private byte[] template;

    // JavaFX Image 对象，用于 UI 展示
    private Image fingerprintImage;

    // 模板的 Base64 字符串，用于持久化存储到数据库
    private String templateBase64;

}