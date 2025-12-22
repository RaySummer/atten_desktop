package com.ray.atten.desktop.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigRepo {
    // 存储在用户目录下，例如 C:\Users\YourName\.atten_desktop\server.properties
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".atten_desktop";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "server.properties";

    public static void saveConfig(String protocol, String host, String port, String theme) {
        Properties props = new Properties();
        props.setProperty("server.protocol", protocol);
        props.setProperty("server.host", host);
        props.setProperty("server.port", port);
        props.setProperty("ui.theme", theme); // 保存主题

        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                props.store(out, "Server Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Properties loadConfig() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }

    // 新增：专门用于只保存主题的方法（在设置界面切换时调用）
    public static void saveTheme(String theme) {
        Properties props = loadConfig();
        props.setProperty("ui.theme", theme);
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                props.store(out, "UI Theme Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 新增：便捷获取当前主题
    public static String getTheme() {
        return loadConfig().getProperty("ui.theme", "light"); // 默认亮色
    }
}