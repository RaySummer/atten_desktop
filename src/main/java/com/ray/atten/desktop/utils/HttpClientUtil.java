package com.ray.atten.desktop.utils;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
            httpGet.setHeader("Accept", AppConstants.MIME_TYPE_APPLICATION_JSON);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (response.getEntity() == null) {
                    return ""; // 無響應體
                }

                String responseBody = EntityUtils.toString(response.getEntity(), AppConstants.DEFAULT_CHARSET);

                if (statusCode >= 200 && statusCode < 300) {
                    return responseBody;
                } else {
                    // 拋出異常，包含詳細的響應信息
                    throw new IOException("HTTP GET 請求失敗，狀態碼: " + statusCode + ", 響應: " + responseBody);
                }
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

            httpPost.setHeader("Content-Type", AppConstants.MIME_TYPE_APPLICATION_JSON);
            httpPost.setHeader("Accept", AppConstants.MIME_TYPE_APPLICATION_JSON);

            if (jsonBody != null && !jsonBody.isEmpty()) {
                StringEntity requestEntity = new StringEntity(jsonBody, AppConstants.DEFAULT_CHARSET);
                httpPost.setEntity(requestEntity);
            }

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (response.getEntity() == null) {
                    return "";
                }

                String responseBody = EntityUtils.toString(response.getEntity(), AppConstants.DEFAULT_CHARSET);

                if (statusCode >= 200 && statusCode < 300) {
                    return responseBody;
                } else {
                    throw new IOException("HTTP POST 請求失敗，狀態碼: " + statusCode + ", 響應: " + responseBody);
                }
            }
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

}
