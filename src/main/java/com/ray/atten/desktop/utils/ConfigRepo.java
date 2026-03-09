package com.ray.atten.desktop.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigRepo {
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".atten_desktop";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "server.properties";

    /**
     * 核心保存逻辑（私有化，统一入口）
     * 采用 synchronized 确保多线程环境下（如异步更新任务和UI操作同时进行）配置文件的安全写入
     */
    private static synchronized void storeProperties(Properties props, String comment) {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("无法创建配置目录: " + CONFIG_DIR);
                return;
            }
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                props.store(out, comment);
                // 强制刷新到底层设备，确保在进程被 kill 前数据已落盘
                out.getFD().sync();
            }
        } catch (IOException e) {
            System.err.println("配置文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 加载配置（统一入口）
     */
    public static Properties loadConfig() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("配置文件加载失败: " + e.getMessage());
            }
        }
        return props;
    }

    /**
     * 保存 Token
     */
    public static void saveToken(String token) {
        Properties props = loadConfig();
        if (token == null) {
            props.remove("auth.token");
        } else {
            props.setProperty("auth.token", token);
        }
        storeProperties(props, "Token Updated");
    }

    /**
     * 获取 Token
     */
    public static String getToken() {
        return loadConfig().getProperty("auth.token");
    }

    // --- 业务方法 ---

    /**
     * 全量保存配置（用于初始化设置页面）
     */
    public static void saveConfig(String protocol, String host, String port, String theme, String version) {
        Properties props = loadConfig(); // 基于现有配置修改，防止丢失未提及的字段
        props.setProperty("server.protocol", protocol != null ? protocol : "http://");
        props.setProperty("server.host", host != null ? host : "");
        props.setProperty("server.port", port != null ? port : "80");
        props.setProperty("ui.theme", theme != null ? theme : "light");
        props.setProperty("app.version", version != null ? version : AppConstants.CURRENT_VERSION);

        //todo: 添加token配置，打开界面时验证token是否存在和有效，否则重新登录

        storeProperties(props, "Full Configuration Update");
    }

    /**
     * 重载旧方法，适配原有 Controller 调用
     */
    public static void saveConfig(String protocol, String host, String port, String theme) {
        saveConfig(protocol, host, port, theme, getVersion());
    }

    /**
     * 极简更新：只更新版本号（更新脚本专用）
     */
    public static void saveVersion(String version) {
        Properties props = loadConfig();
        props.setProperty("app.version", version);
        storeProperties(props, "Version Updated by Updater");
    }

    /**
     * 极简更新：只更新主题
     */
    public static void saveTheme(String theme) {
        Properties props = loadConfig();
        props.setProperty("ui.theme", theme);
        storeProperties(props, "Theme Updated");
    }

    /**
     * 获取版本（带默认值）
     */
    public static String getVersion() {
        return loadConfig().getProperty("app.version", AppConstants.CURRENT_VERSION);
    }

    /**
     * 获取主题（带默认值）
     */
    public static String getTheme() {
        return loadConfig().getProperty("ui.theme", "light");
    }
}