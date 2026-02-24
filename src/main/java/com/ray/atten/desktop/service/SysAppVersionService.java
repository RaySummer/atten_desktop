package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.ConfigRepo;
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
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 调用远程接口检查更新
     */
    public Map<String, Object> checkUpdateFromServer(String currentVersion) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("currentVersion", currentVersion);

        String responseJson = HttpClientUtil.doGet(AppConstants.getApiVersionCheck(), params);

        JsonNode rootNode = objectMapper.readTree(responseJson);
        if (!"200".equals(rootNode.get("status").asText())) {
            return null;
        }

        JsonNode contentNode = rootNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }

        return objectMapper.convertValue(contentNode, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 创建下载任务
     */
    public Task<File> createDownloadTask(String relativeUrl) {
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

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("服务器响应异常，状态码: " + conn.getResponseCode());
                }

                long totalSize = conn.getContentLengthLong();

                // --- 【核心修改开始】：修改下载存储路径 ---
                // 不再使用 System.getProperty("java.io.tmpdir")
                // 改为使用用户根目录，这与你的 ConfigRepo 存放位置保持一致，权限更稳定
                String userHome = System.getProperty("user.home");
                File tempDir = new File(userHome + File.separator + ".atten_desktop", "update_cache");

                if (!tempDir.exists() && !tempDir.mkdirs()) {
                    throw new IOException("无法创建下载目录: " + tempDir.getAbsolutePath());
                }

                File targetFile = new File(tempDir, "update_new.jar");
                // --- 【核心修改结束】 ---

                if (targetFile.exists()) {
                    targetFile.delete();
                }

                try (InputStream is = conn.getInputStream();
                     BufferedInputStream bis = new BufferedInputStream(is);
                     FileOutputStream fos = new FileOutputStream(targetFile)) {

                    byte[] buffer = new byte[8192];
                    int len;
                    long downloaded = 0;

                    while ((len = bis.read(buffer)) != -1) {
                        if (isCancelled()) return null;
                        fos.write(buffer, 0, len);
                        downloaded += len;
                        updateProgress(downloaded, totalSize);
                        updateMessage(String.format("%.2f MB / %.2f MB",
                                downloaded / 1024.0 / 1024.0, totalSize / 1024.0 / 1024.0));
                    }
                }
                updateMessage("下载完成，准备安装...");
                return targetFile;
            }
        };
    }

    public void executeUpdaterScript(String version) {
        try {
            String userHome = System.getProperty("user.home");
            File tempNewJar = new File(userHome + File.separator + ".atten_desktop" + File.separator + "update_cache", "update_new.jar");

            File currentJarFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            File appDir = currentJarFile.getParentFile();
            File batchFile = new File(appDir, "updater.bat");

            ConfigRepo.saveVersion(version);

            // --- 核心修正：构建绝对纯净的命令行 ---
            // 我们手动给参数套上引号，并使用 /K 而不是 /C
            // /K 的作用是：如果执行失败，窗口会保持打开，不会消失！
            String cmdCommand = String.format("cmd.exe /k \"\"%s\" \"%s\" \"%s\" \"%s\"\"",
                    batchFile.getAbsolutePath(),
                    tempNewJar.getAbsolutePath(),
                    appDir.getAbsolutePath(),
                    "atten_desktop-latest.jar");

            System.out.println("准备执行: " + cmdCommand);

            // 使用 Runtime 执行，这种方式在处理这种嵌套引号的 CMD 命令时有时比 ProcessBuilder 更稳
            Runtime.getRuntime().exec(cmdCommand);

            // 留出时间让 CMD 窗口弹出来
            Thread.sleep(1000);
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}