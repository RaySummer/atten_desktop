package com.ray.atten.desktop.utils;

import org.apache.http.Consts;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * 應用程序中所有全局、靜態的常量定義。
 * 包括 API 地址、HTTP 相關配置、字符集等。
 */
public class AppConstants {
    // 客户端版本 需与 pom.xml 保持一致
    public static String CURRENT_VERSION;

    static {
        try {
            Properties props = new Properties();
            props.load(AppConstants.class.getResourceAsStream("/version.properties"));
            CURRENT_VERSION = props.getProperty("app.version");
        } catch (Exception e) {
            CURRENT_VERSION = "1.0.0"; // 兜底
        }
    }

    /**
     * Atten Middle 服務的基礎 URL。
     * 建議未來從外部配置 (如 properties/YAML) 中讀取。
     */
    public static String API_BASE_URL = "http://localhost:8821";

    public static String YYYY_MM_DD_HH_mm_SS = "yyyy-MM-dd HH:mm:ss";

    public static String YYYY_MM_DD = "yyyy-MM-dd";

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
     * 員工查詢
     */
    public static String    getEmployeeSearchAPI() {
        return API_BASE_URL + "/api/oa-employees/query";
    }

    /**
     * 查询考勤数据
     */
    public static String getAttendanceLogSearchAPI() {
        return API_BASE_URL + "/api/atten/query";
    }

    /**
     * 获取考勤组
     */
    public static String getAttendanceDeviceSearchAPI() {
        return API_BASE_URL + "/api/atten/group";
    }

    /**
     * 同步人员/指纹/照片到Middle
     */
    public static String getSyncToMiddleAPI() {
        return API_BASE_URL + "/api/sync/sync-employee";
    }

    /**
     * 同步考勤数据
     */
    public static String getSyncAttendanceAPI() {
        return API_BASE_URL + "/api/device-commend";
    }

    /**
     * 获取考勤机列表
     */
    public static String getDeviceSearchAPI() {
        return API_BASE_URL + "/api/device/query";
    }

    /**
     * 添加或修改考勤机
     */
    public static String getAddOrUpdateDeviceAPI() {
        return API_BASE_URL + "/api/management/devices";
    }

    /**
     * 获取打印模板
     */
    public static String getCardTemplatesAPI() {
        return API_BASE_URL + "/api/card-template/list-active";
    }


    /**
     * 获取打印凭证
     */
    public static String getPreparePrintAPI() {
        return API_BASE_URL + "/api/card-template/prepare-print";
    }

    /**
     * 从浏览器打开打印页面
     */
    public static String getGotoPrintAPI() {
        return API_BASE_URL + "/badge_print.html";
    }

    /**
     * 检查客户端是否有新版本
     */
    public static String getApiVersionCheck(String currentVersion) {
        return API_BASE_URL + "/api/version/check?currentVersion=" + currentVersion;
    }


    public static String dateTimeFormatter(LocalDateTime dateTime, String formatType) {
        if (dateTime == null) {
            return "";
        }
        // 定義目標格式 (注意：MM 是月份，mm 是分鐘)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatType);
        return formatter.format(dateTime);
    }

}
