package com.ray.atten.desktop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.HttpClientUtil;
import com.ray.atten.desktop.utils.SessionContext;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行登录业务
     *
     * @return 登录成功返回 true
     */
    public boolean login(String username, String password) throws Exception {
        // 1. 组装请求体
        Map<String, String> loginMap = new HashMap<>();
        loginMap.put("username", username);
        loginMap.put("password", password);
        String jsonBody = objectMapper.writeValueAsString(loginMap);

        // 2. 调用中间台接口
        String url = AppConstants.getLoginAPI();
        String responseJson = HttpClientUtil.doPost(url, jsonBody);

        // 3. 解析基础响应
        JsonNode rootNode = objectMapper.readTree(responseJson);
        String token = rootNode.path("token").asText();

        if (token != null && !token.isEmpty()) {
            // 4. 解析 JWT 载荷获取角色
            boolean isSuper = checkSuperAdminFromToken(token);

            // 5. 更新全局会话状态
            SessionContext.setToken(token);
            SessionContext.setUsername(username);
            SessionContext.setSuperAdmin(isSuper); // 设置超级管理员状态

            ConfigRepo.saveToken(token);

            return true;
        }
        return false;
    }

    /**
     * 从 JWT Token 中解析角色信息
     */
    private boolean checkSuperAdminFromToken(String token) {
        try {
            // JWT 格式为 Header.Payload.Signature，我们需要解析 Payload (第二部分)
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;

            String payloadJson = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);

            // 获取我们在 middle 端存入的 "role" 字段
            String role = payloadNode.path("role").asText();

            // 匹配超级管理员角色名
            return "ROLE_SUPER_ADMIN".equals(role);
        } catch (Exception e) {
            // 解析失败默认为非超管
            return false;
        }
    }

    /**
     * 验证本地 Token 是否有效并初始化会话
     */
    public boolean checkTokenAndLogin(String token) {
        if (token == null || token.isEmpty()) return false;

        try {
            // 设置临时的 Token 到 SessionContext 供拦截器使用
            SessionContext.setToken(token);

            // 调用后端一个“轻量级”接口验证 Token
            // 比如：GET /api/auth/me 或者之前的获取公司列表接口
            String url = AppConstants.getValidateTokenAPI();
            String responseJson = HttpClientUtil.doGet(url, null);

            // 解析响应，更新 SessionContext
            JsonNode rootNode = objectMapper.readTree(responseJson);
            String username = rootNode.path("username").asText();

            // 解析角色（复用之前写的解析 JWT 方法）
            boolean isSuper = checkSuperAdminFromToken(token);

            SessionContext.setUsername(username);
            SessionContext.setSuperAdmin(isSuper);

            return true;
        } catch (Exception e) {
            // Token 过期或网络不通
            SessionContext.logout();
            ConfigRepo.saveToken(null); // 清除无效 Token
            return false;
        }
    }

}
