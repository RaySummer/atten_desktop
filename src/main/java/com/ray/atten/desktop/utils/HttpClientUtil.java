package com.ray.atten.desktop.utils;

import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

public class HttpClientUtil {

    private HttpClientUtil() {
        // 私有構造函數，防止實例化
    }

    /**
     * 统一为请求添加 Header
     */
    private static void setCommonHeaders(HttpRequestBase request) {
        request.setHeader("Accept", AppConstants.MIME_TYPE_APPLICATION_JSON);
        request.setHeader("Content-Type", AppConstants.MIME_TYPE_APPLICATION_JSON);

        // 关键点：如果 SessionContext 中有 Token，则自动注入 Header
        String token = SessionContext.getToken();
        if (token != null && !token.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + token);
        }
    }

    /**
     * 發送 GET 請求，支持 URL 參數。
     * * @param baseUrl 基礎 URL (不帶參數), e.g., http://localhost:8080/api/employees
     *
     * @param params 查詢參數 Map
     * @return 服務器響應的 JSON 字符串
     * @throws IOException 如果發生網絡或 IO 錯誤
     */
    public static String doGet(String baseUrl, Map<String, String> params) throws IOException {
        String fullUrl = buildUrlWithParams(baseUrl, params);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(fullUrl);
            setCommonHeaders(httpGet); // 调用统一 Header 设置

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                return handleResponse(response);
            }
        }
    }

    /**
     * 發送 POST 請求，用於提交 JSON 數據。
     *
     * @param url      完整的請求 URL
     * @param jsonBody 要發送的 JSON 字符串
     * @return 服務器響應的 JSON 字符串
     * @throws IOException 如果發生網絡或 IO 錯誤
     */
    public static String doPost(String url, String jsonBody) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            setCommonHeaders(httpPost); // 调用统一 Header 设置

            if (jsonBody != null && !jsonBody.isEmpty()) {
                httpPost.setEntity(new StringEntity(jsonBody, AppConstants.DEFAULT_CHARSET));
            }

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return handleResponse(response);
            }
        }
    }

    /**
     * 提取公共的响应处理逻辑
     */
    private static String handleResponse(CloseableHttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        String responseBody = response.getEntity() != null ?
                EntityUtils.toString(response.getEntity(), AppConstants.DEFAULT_CHARSET) : "";

        if (statusCode >= 200 && statusCode < 300) {
            return responseBody;
        } else if (statusCode == 401 || statusCode == 403) {
            throw new IOException("权限验证失败，请重新登录。");
        } else {
            throw new IOException("请求失败 [" + statusCode + "]: " + responseBody);
        }
    }

    /**
     * 輔助方法：構造帶有 URL 編碼參數的完整 URL。
     */
    private static String buildUrlWithParams(String baseUrl, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?");

        params.forEach((key, value) -> {
            try {
                // 確保對值進行 URL 編碼
                String encodedValue = URLEncoder.encode(value, AppConstants.DEFAULT_CHARSET.name());
                urlBuilder.append(key).append("=").append(encodedValue).append("&");
            } catch (Exception e) {
                // 忽略編碼錯誤，繼續下一個參數
            }
        });

        // 移除末尾多餘的 "&"
        if (urlBuilder.charAt(urlBuilder.length() - 1) == '&') {
            urlBuilder.setLength(urlBuilder.length() - 1);
        }

        return urlBuilder.toString();
    }

    public static String doPut(String url, String jsonBody) throws Exception {
        HttpPut httpPut = new HttpPut(url);
        // 注入 Token
        httpPut.setHeader("Authorization", "Bearer " + SessionContext.getToken());
        httpPut.setHeader("Content-Type", "application/json;charset=utf-8");

        if (jsonBody != null) {
            httpPut.setEntity(new StringEntity(jsonBody, "UTF-8"));
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpPut)) {
            return handleResponse(response); // 处理状态码并返回字符串
        }
    }

    public static String doDelete(String url) throws Exception {
        HttpDelete httpDelete = new HttpDelete(url);
        httpDelete.setHeader("Authorization", "Bearer " + SessionContext.getToken());

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpDelete)) {
            return handleResponse(response);
        }
    }

}
