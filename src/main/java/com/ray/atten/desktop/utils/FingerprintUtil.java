package com.ray.atten.desktop.utils;

import com.ray.atten.desktop.dto.FingerprintResult;
import javafx.scene.image.Image;
import com.zkteco.biometric.FingerprintSensorEx; // ZKFinger SDK 核心类
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.Objects;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ZKTeco Live20R 指纹仪操作工具类。
 * 封装了 ZKFinger SDK 的核心方法，实现指纹录入、模板提取和比对。
 */
public class FingerprintUtil {

    // ZKTeco SDK 错误码：0 表示成功
    private static final int ZK_SUCCESS = 0;

    // 指纹模板的最大大小 (2048 Bytes)
    private static final int MAX_TEMPLATE_SIZE = 2048;

    // 默认的 1:1 比对阈值 (默认 35) [cite: 218]
    private static final int DEFAULT_VERIFY_THRESHOLD = 35;

    // SDK 状态和句柄
    private static boolean isSDKInitialized = false;
    private static long deviceHandle = 0;     // 设备句柄 [cite: 107]
    private static long dbHandle = 0;         // 算法句柄 [cite: 212]
    private static int imageWidth = 0;        // 图像宽度 [cite: 218]
    private static int imageHeight = 0;       // 图像高度 [cite: 218]

    private static final String DEFAULT_FINGERPRINT_IMG = "/images/default_fingerprint.png";

    // ==================== 1. 核心初始化与销毁 ====================

    /**
     * 初始化 SDK 资源、算法库并连接设备。
     *
     * @return 成功返回 true，失败返回 false
     */
    public static boolean initAndConnect() {
        if (deviceHandle != 0) {
            System.out.println("Fingerprint device already connected.");
            return true;
        }

        // 1. 初始化 SDK 资源 [cite: 76]
        if (!isSDKInitialized) {
            int ret = FingerprintSensorEx.Init();
            if (ret != ZK_SUCCESS) {
                System.err.println("Fingerprint SDK Init failed. Code: " + ret);
                return false;
            }
            isSDKInitialized = true;
        }

        // 2. 初始化算法库 (获取算法句柄) [cite: 212]
        dbHandle = FingerprintSensorEx.DBInit();
        if (dbHandle == 0) {
            System.err.println("Fingerprint DBInit failed.");
            return false;
        }

        // 3. 连接设备 (index=0 表示第一个设备) [cite: 96]
        deviceHandle = FingerprintSensorEx.OpenDevice(0);
        if (deviceHandle == 0) {
            System.err.println("Failed to connect Live20R. OpenDevice failed. Code: -1002 or -1003 etc. See 4.2");
            // 连接失败时释放算法库
            FingerprintSensorEx.DBFree(dbHandle);
            dbHandle = 0;
            return false;
        }

        // 4. 获取图像参数 (宽度和高度，用于采集时预分配缓存) [cite: 218]
        if (!getImageParameters(deviceHandle)) {
            // 失败时不关闭连接，但打印错误
            System.err.println("Failed to get image parameters. Capture may fail.");
        }

        System.out.println("Live20R connected. Device Handle: " + deviceHandle + ", DB Handle: " + dbHandle);
        return true;
    }

    /**
     * 获取指纹图像的宽度和高度。
     *
     * @param devHandle 设备句柄
     * @return 成功返回 true，失败返回 false
     */
    private static boolean getImageParameters(long devHandle) {
        // 参数代码 1: 图像宽 [cite: 218]
        imageWidth = getParameterInt(devHandle, 1);
        // 参数代码 2: 图像高 [cite: 218]
        imageHeight = getParameterInt(devHandle, 2);

        if (imageWidth <= 0 || imageHeight <= 0) {
            System.err.println("Invalid Image Width/Height obtained: " + imageWidth + "x" + imageHeight);
            return false;
        }
        return true;
    }

    /**
     * 辅助方法：获取 SDK 整型参数 (参数代码 1, 2 等)。
     *
     * @param devHandle 设备句柄
     * @param code      参数代码
     * @return 参数值，失败返回 -1
     */
    private static int getParameterInt(long devHandle, int code) {
        byte[] value = new byte[4];
        int[] len = new int[1];
        len[0] = 4; // sizeof int

        // public static int GetParameters(long devHandle, int code, byte[] paramValue, int[] size) [cite: 151, 152]
        int ret = FingerprintSensorEx.GetParameters(devHandle, code, value, len);
        if (ret == ZK_SUCCESS) {
            // 将 4 字节的 byte 数组转换为 int (假设使用小端序) [cite: 174]
            return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
        return -1;
    }

    /**
     * 断开连接并释放 SDK 资源。
     */
    public static void destroy() {
        try {
            if (deviceHandle != 0) {
                // public static int CloseDevice(long devHandle) [cite: 112]
                FingerprintSensorEx.CloseDevice(deviceHandle);
                deviceHandle = 0;
            }
            if (dbHandle != 0) {
                // public static int DBFree(long dbHandle) [cite: 212]
                FingerprintSensorEx.DBFree(dbHandle);
                dbHandle = 0;
            }
            if (isSDKInitialized) {
                // public static int Terminate () [cite: 87]
                FingerprintSensorEx.Terminate();
                isSDKInitialized = false;
            }
            System.out.println("Fingerprint SDK resources released.");
        } catch (Exception e) {
            System.err.println("Failed to destroy Fingerprint SDK: " + e.getMessage());
        }
    }

    // ==================== 2. 核心业务方法 ====================
    /**
     * 採集指紋並提取模板（帶重試機制，解決 Code -8 不穩定問題）
     * @param timeoutMillis 最大超時時間（毫秒），防止無限循環，例如設置為 5000 (5秒)
     * @return 採集結果，超時或失敗返回 null
     */
    public static FingerprintResult captureAndExtract(long timeoutMillis) {
        if (deviceHandle == 0 || dbHandle == 0 || imageWidth <= 0 || imageHeight <= 0) {
            System.err.println("設備未就緒，請先調用 initAndConnect");
            return null;
        }

        byte[] imgBuffer = new byte[imageWidth * imageHeight];
        byte[] template = new byte[MAX_TEMPLATE_SIZE];
        int[] size = new int[1];

        long startTime = System.currentTimeMillis();

        // 開始循環採集
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            // 【關鍵】每次循環都要重置 size[0]，否則 SDK 可能會沿用上次的長度導致報錯
            size[0] = MAX_TEMPLATE_SIZE;

            // 嘗試採集
            int ret = FingerprintSensorEx.AcquireFingerprint(deviceHandle, imgBuffer, template, size);

            if (ret == ZK_SUCCESS) {
                // 情況 1：採集並提取成功
                System.out.println("指紋採集成功，模板長度: " + size[0]);
                return createResultObject(imgBuffer, template, size[0]);

            } else if (ret == -8) {
                // 情況 2：圖像抓取了，但特徵提取失敗（手指按得輕、太乾等）
                // 這裡不 return，而是繼續下一次循環，給用戶機會重新按壓
                System.out.println("特徵提取失敗(Code -8)，正在嘗試重新抓取...");

                try {
                    // 短暫休眠，避免過度消耗 CPU
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;

            } else if (ret == -18) {
                // 情況 3：操作被取消（通常是調用了 CloseDevice 或 Terminate）
                System.out.println("採集操作已取消(-18)");
                return null;
            } else {
                // 情況 4：其他硬件錯誤（如 -1 設備斷開）
                System.err.println("採集發生硬件錯誤，代碼: " + ret);
                break;
            }
        }

        System.err.println("採集超時，未能在規定時間內獲取有效指紋。");
        return null;
    }

    /**
     * 私有輔助方法：封裝結果對象
     */
    private static FingerprintResult createResultObject(byte[] imgBuffer, byte[] template, int actualLen) {
        FingerprintResult result = new FingerprintResult();

        // 1. 截取有效模板
        byte[] actualTemplate = new byte[actualLen];
        System.arraycopy(template, 0, actualTemplate, 0, actualLen);
        result.setTemplate(actualTemplate);

        // 2. 轉為 Base64
        result.setTemplateBase64(FingerprintSensorEx.BlobToBase64(actualTemplate, actualLen));

        // 3. 圖像轉換（建議使用之前給你的 WritableImage 邏輯）
        result.setFingerprintImage(rawImageToFxImage(imgBuffer, imageWidth, imageHeight));

        return result;
    }

    /**
     * 比对两个指纹模板。
     *
     * @param temp1         模板 1 (byte[])
     * @param temp2         模板 2 (byte[])
     * @param securityLevel 比对安全级别（通常 SDK 使用内部阈值，这里只需比对）
     * @return 比对成功返回 true (得分 >= 阈值)，失败返回 false
     */
    public static boolean verify(byte[] temp1, byte[] temp2, int securityLevel) {
        if (dbHandle == 0 || temp1 == null || temp2 == null) {
            System.err.println("Verification failed: DB not initialized or template is null.");
            return false;
        }

        // public int DBMatch(long dbHandle , byte[] temp1, byte[] temp2) [cite: 214]
        int score = FingerprintSensorEx.DBMatch(dbHandle, temp1, temp2);

        if (score > ZK_SUCCESS) {
            // 比对分数大于等于默认阈值 (35) 视为成功 [cite: 214, 218]
            boolean match = score >= DEFAULT_VERIFY_THRESHOLD;
            System.out.println("Fingerprint verification score: " + score + ". Match: " + match);
            return match;
        } else {
            System.err.println("Fingerprint verification failed. Code: " + score);
            return false;
        }
    }

    // ==================== 3. 图像/Base64 辅助方法 ====================

    /**
     * 【待实现】将 SDK 返回的原始图像数据转换为 JavaFX Image 对象。
     *
     * @param rawData 原始图像数据 (width*height Bytes, 8位灰度)
     * @param width   图像宽度
     * @param height  图像高度
     * @return JavaFX Image
     */
    private static Image rawImageToFxImage(byte[] rawData, int width, int height) {
        if (rawData == null) return null;

        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter pw = writableImage.getPixelWriter();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // rawData[i] 是灰度值 (0-255)
                int gray = rawData[y * width + x] & 0xff;
                // 转换为 ARGB (不透明)
                int argb = (0xff << 24) | (gray << 16) | (gray << 8) | gray;
                pw.setArgb(x, y, argb);
            }
        }
        return writableImage;
    }

    /**
     * 将 byte 数组转换为 Base64 字符串 (使用 SDK 提供的函数)。
     *
     * @param buf 二进制数据
     * @return Base64 格式字符串
     */
    public static String blobToBase64(byte[] buf) {
        if (buf == null || buf.length == 0) return null;
        // public static String BlobToBase64(byte[] buf, int cbBuf) [cite: 216]
        return FingerprintSensorEx.BlobToBase64(buf, buf.length);
    }

    /**
     * 将 Base64 字符串转换为 byte 数组 (使用 SDK 提供的函数)。
     *
     * @param strBase64 Base64 格式字符串
     * @return byte 数组
     */
    public static byte[] base64ToBlob(String strBase64) {
        if (strBase64 == null || strBase64.isEmpty()) return null;

        // 预估返回数组大小，实际应根据 Base64 长度预估或使用 MAX_TEMPLATE_SIZE
        byte[] buf = new byte[MAX_TEMPLATE_SIZE];

        // public static int Base64ToBlob(String strBase64, byte[] buf, int cbBuf) [cite: 217]
        int actualLen = FingerprintSensorEx.Base64ToBlob(strBase64, buf, buf.length);

        if (actualLen > 0) {
            byte[] result = new byte[actualLen];
            System.arraycopy(buf, 0, result, 0, actualLen);
            return result;
        }
        return null;
    }

    /**
     * 从 BMP 或 JPG 图片文件中提取指纹模板 (SDK 标准版功能)
     *
     * @param filePath 图片全路径 [cite: 216]
     * @param dpi      图像 DPI [cite: 216]
     * @return 提取的模板 byte[]，失败返回 null
     */
    public static byte[] extractFromImage(String filePath, int dpi) {
        if (dbHandle == 0 || filePath == null) {
            System.err.println("DB not initialized or filePath is null.");
            return null;
        }

        byte[] template = new byte[MAX_TEMPLATE_SIZE];
        int[] size = new int[1];

        size[0] = MAX_TEMPLATE_SIZE;

        // public int ExtractFromImage(long dbHandle , String filePath, int DPI, byte[] template, int[] size) [cite: 216]
        int ret = FingerprintSensorEx.ExtractFromImage(dbHandle, filePath, dpi, template, size);

        if (ret == ZK_SUCCESS) {
            byte[] actualTemplate = new byte[size[0]];
            System.arraycopy(template, 0, actualTemplate, 0, size[0]);
            System.out.println("Template extracted from image. Size: " + size[0]);
            return actualTemplate;
        } else {
            System.err.println("Failed to extract template from image. Code: " + ret);
            return null;
        }
    }

}