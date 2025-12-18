package com.ray.atten.desktop.utils;

import org.apache.http.Consts;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 應用程序中所有全局、靜態的常量定義。
 * 包括 API 地址、HTTP 相關配置、字符集等。
 */
public class AppConstants {
    /**
     * Atten Middle 服務的基礎 URL。
     * 建議未來從外部配置 (如 properties/YAML) 中讀取。
     */
    public static String API_BASE_URL = "http://localhost:8821";

    /**
     * 核心优化：由 Controller 调用，根据界面输入的三部分拼接完整的 URL
     * * @param protocol "http://" 或 "https://"
     *
     * @param host "192.168.1.10" 或 "api.server.com"
     * @param port "8821" 或 "443"
     */
    public static void updateApiBaseUrl(String protocol, String host, String port) {
        // 1. 去掉用户可能重复输入的 http:// 前缀
        String cleanHost = host.replace("http://", "").replace("https://", "");

        // 2. 拼接 URL
        if (port == null || port.trim().isEmpty()) {
            // 如果没写端口，根据协议判断默认端口（可选逻辑）
            API_BASE_URL = protocol + cleanHost;
        } else {
            API_BASE_URL = protocol + cleanHost + ":" + port;
        }

        System.out.println("[Config] API_BASE_URL 已更新为: " + API_BASE_URL);
    }

    // -----------------------------------------------------
    // 2. HTTP/JSON 相關配置
    // -----------------------------------------------------

    /**
     * HTTP Content-Type/Accept: JSON 類型。
     */
    public static final String MIME_TYPE_APPLICATION_JSON = "application/json";

    /**
     * 默認字符集: UTF-8。
     */
    public static final Charset DEFAULT_CHARSET = Consts.UTF_8;

    /**
     * 默認字符集名稱。
     */
    public static final String DEFAULT_CHARSET_NAME = "UTF-8";

    // -----------------------------------------------------
    // 3. 應用程序內部配置 (例如分頁)
    // -----------------------------------------------------

    /**
     * 默認每頁顯示的員工數量。
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    // 私有構造函數，防止實例化
    private AppConstants() {
        throw new UnsupportedOperationException("This is a constants utility class and cannot be instantiated.");
    }

    //-----------------------
    // 需要调用的API地址全部写到这里
    //-----------------------
    /**
     * 員工查詢的具體 API 端點。
     */
    public static final String API_EMPLOYEE_SEARCH = API_BASE_URL + "/api/oa-employees/query";
    /**
     * 获取考勤组
     */
    public static final String API_ATTENDANCE_DEVICE_SEARCH = API_BASE_URL + "/api/oa-employees/atten-group";
    /**
     * 同步人员/指纹/照片到Middle
     */
    public static final String API_SYNC_TO_MIDDLE = API_BASE_URL + "/api/sync/sync-employee";


    public static String dateTimeFormatter(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        // 定義目標格式 (注意：MM 是月份，mm 是分鐘)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return formatter.format(dateTime);
    }

}
