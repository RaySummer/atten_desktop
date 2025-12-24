package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.HttpClientUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class SysAppVersionService {

    @Autowired
    private ObjectMapper objectMapper;

    public SysAppVersionService() {
        this.objectMapper = new ObjectMapper();

        // 【核心修正】註冊 JSR310 模塊來處理 Java 8 日期時間
        this.objectMapper.registerModule(new JavaTimeModule());

    }

    /**
     * 调用远程接口检查更新
     */
    public Map<String, Object> checkUpdateFromServer(String currentVersion) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("currentVersion", currentVersion);

        String baseUrl = AppConstants.API_BASE_URL + "/api/version/check";
        // 使用你的 HttpClientUtil 获取字符串
        String responseJson = HttpClientUtil.doGet(baseUrl, params);

        // 使用 ObjectMapper 解析
        JsonNode rootNode = objectMapper.readTree(responseJson);

        // 校验状态
        if (!"200".equals(rootNode.get("status").asText())) {
            return null;
        }

        JsonNode contentNode = rootNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }

        // 将 content 转为 Map 返回给 Controller
        return objectMapper.convertValue(contentNode, new TypeReference<Map<String, Object>>() {
        });
    }


    /**
     * 创建下载任务
     *
     * @param relativeUrl 相对地址
     * @return 返回 Task 对象，让 Controller 可以绑定进度条
     */
    public Task<File> createDownloadTask(String relativeUrl) {
        // 拼接完整的下载 URL
        String baseUrl = AppConstants.API_BASE_URL;
        if (baseUrl.endsWith("/") && relativeUrl.startsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String fullUrl = baseUrl + relativeUrl;

        return new Task<File>() {
            @Override
            protected File call() throws Exception {
                updateMessage("正在连接服务器...");

                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // 检查响应状态
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("服务器响应异常，状态码: " + responseCode);
                }

                // 获取文件总大小 (需要中台设置 Content-Length)
                long totalSize = conn.getContentLengthLong();

                // --- 核心修复：使用系统临时目录 ---
                String tempPath = System.getProperty("java.io.tmpdir");
                File tempDir = new File(tempPath, "atten_update");
                if (!tempDir.exists() && !tempDir.mkdirs()) {
                    throw new IOException("无法创建临时目录: " + tempDir.getAbsolutePath());
                }

                File targetFile = new File(tempDir, "update_new.jar");
                // 如果旧的更新残留文件存在，先删除
                if (targetFile.exists()) {
                    targetFile.delete();
                }

                // 开始下载流处理
                try (InputStream is = conn.getInputStream();
                     BufferedInputStream bis = new BufferedInputStream(is);
                     FileOutputStream fos = new FileOutputStream(targetFile)) {

                    byte[] buffer = new byte[8192];
                    int len;
                    long downloaded = 0;

                    while ((len = bis.read(buffer)) != -1) {
                        if (isCancelled()) {
                            fos.close();
                            return null;
                        }

                        fos.write(buffer, 0, len);
                        downloaded += len;

                        // 更新进度条百分比
                        updateProgress(downloaded, totalSize);

                        // 更新进度文字描述
                        String status = String.format("%.2f MB / %.2f MB",
                                downloaded / 1024.0 / 1024.0, totalSize / 1024.0 / 1024.0);
                        updateMessage(status);
                    }
                }
                updateMessage("下载完成，准备安装...");
                return targetFile;
            }
        };
    }

    /**
     * 执行更新脚本
     * 此方法会计算路径、启动外部进程并关闭当前程序
     */
    public void executeUpdaterScript() {
        try {
            // 1. 定位下载好的临时文件
            String tempPath = System.getProperty("java.io.tmpdir");
            File tempNewJar = new File(tempPath, "atten_update/update_new.jar");

            if (!tempNewJar.exists()) {
                throw new IOException("找不到已下载的更新包");
            }

            // 2. 获取程序运行根目录（EXE 所在目录）
            String userDir = System.getProperty("user.dir");
            File rootDir = new File(userDir);

            // 3. 寻找 updater.bat
            File batchFile = new File(rootDir, "updater.bat");
            if (!batchFile.exists()) {
                batchFile = new File(rootDir, "app/updater.bat"); // 兼容 app 目录下
            }

            if (!batchFile.exists()) {
                throw new IOException("未找到 updater.bat，位置: " + rootDir.getAbsolutePath());
            }

            // 4. 【关键修正】获取当前 Jar 的信息
            // 无论当前运行的是 1.0.2 还是 1.0.5，获取它的绝对路径和名字
            File currentJarFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            String jarDirectory = currentJarFile.getParent();
            String currentJarName = currentJarFile.getName(); // 得到当前运行的真实文件名

            // 5. 启动脚本
            // 参数说明:
            // %1: tempNewJar (下载好的 1.0.5 内容)
            // %2: jarDirectory (app 目录)
            // %3: currentJarName (目标文件名，可能是 atten_desktop-latest.jar)
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c", "start", "/min",
                    batchFile.getAbsolutePath(),
                    tempNewJar.getAbsolutePath(),
                    jarDirectory,
                    currentJarName
            );

            pb.start();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> CustomAlertDialog.showError("启动更新失败", e.getMessage()));
        }
    }
}
