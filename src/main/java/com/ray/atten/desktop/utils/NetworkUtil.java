package com.ray.atten.desktop.utils;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

public class NetworkUtil {
    // 校验主机名（IP或域名）
    private static final String HOST_REGEX = "^(?=.{1,255}$)[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$";

    public static boolean isValidHost(String host) {
        // 如果需要同时兼容 localhost 或 纯IP，这个正则也能很好处理
        return host != null && (host.matches(HOST_REGEX) || host.equals("localhost"));
    }

    /**
     * 检测指定的协议、IP和端口是否可通
     */
    public static boolean checkConnection(String protocol, String host, int port) {
        try (Socket socket = new Socket()) {
            // 1. 首先尝试建立 Socket 连接（验证 IP 和 Port）
            socket.connect(new InetSocketAddress(host, port), 2000); // 2秒超时

            // 2. 如果 Socket 通了，可以进一步验证协议（可选）
            // 这里可以通过简单的 URLConnection 访问一个心跳接口
            URL url = new URL(protocol + "://" + host + ":" + port + "/ping"); // 假设后台有健康检查接口
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            int code = connection.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
