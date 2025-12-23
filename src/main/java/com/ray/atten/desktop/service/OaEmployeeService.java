package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.dto.GlobalResponseBody;
import com.ray.atten.desktop.dto.OaEmployeeQueryRequest;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.dto.SyncRequest;
import com.ray.atten.desktop.model.AttendanceGroup;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class OaEmployeeService {

    @Autowired
    private final ObjectMapper objectMapper;

    public OaEmployeeService() {
        this.objectMapper = new ObjectMapper();

        // 【核心修正】註冊 JSR310 模塊來處理 Java 8 日期時間
        this.objectMapper.registerModule(new JavaTimeModule());

    }

    public PageResponse<OaEmployee> queryEmployees(OaEmployeeQueryRequest request) {

        String requestJson;
        String responseJson;

        try {
            // 1. DTO 轉 JSON
            requestJson = objectMapper.writeValueAsString(request);

            System.out.println("Request Json ->> " + requestJson);

            // 2. 調用 HttpClientUtil.doPost 發送請求
            responseJson = HttpClientUtil.doPost(AppConstants.getEmployeeSearchAPI(), requestJson);

            // 3. 解析 Middle 服務返回的 GlobalResponseBody 結構
            // 假設 Middle 返回的是 GlobalResponseBody 封裝 Page<OaEmployee>

            GlobalResponseBody globalResponse = objectMapper.readValue(
                    responseJson,
                    GlobalResponseBody.class
            );

            if (!"200".equals(globalResponse.getStatus())) {
                return new PageResponse<>(); // 返回空結果
            }

            // 4. 解析 data 字段的 Page<OaEmployee> 結構
            // 由於 PageResponse 帶有泛型，需要使用 TypeReference 確保 Jackson 正確解析

            // a) 將 GlobalResponseBody 的 data 字段 (Object) 重新轉換為 JSON 字符串
            String pageContentJson = objectMapper.writeValueAsString(globalResponse.getContent());

            // b) 使用 TypeReference 進行泛型安全反序列化
            PageResponse<OaEmployee> pageResponse = objectMapper.readValue(
                    pageContentJson,
                    new TypeReference<PageResponse<OaEmployee>>() {
                    } // 注意這裡使用了 TypeReference
            );

            return pageResponse;

        } catch (IOException e) {
            // 在 JavaFX 應用中，遇到 IO 錯誤通常需要處理或拋出運行時異常
            throw new RuntimeException("無法連接到 Atten Middle 服務: " + e.getMessage(), e);
        }
    }

    public List<AttendanceGroup> getAttendanceGroups() throws IOException {
        // 1. 執行 GET 請求
        String jsonResponse = HttpClientUtil.doGet(AppConstants.getAttendanceDeviceSearchAPI(), null); // 假設 doGet 返回 JSON 字符串

        // 2. 解析 JSON 響應
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // 檢查 status 是否為 "200"
            if (!"200".equals(rootNode.get("status").asText())) {
                throw new IOException("API 錯誤: " + rootNode.get("msg").asText());
            }

            // 獲取 content 節點
            JsonNode contentNode = rootNode.get("content");
            if (contentNode == null || !contentNode.isArray()) {
                return null; // 返回空列表
            }

            // 3. 反序列化 content 列表
            return objectMapper.readValue(contentNode.toString(), new TypeReference<List<AttendanceGroup>>() {
            });

        } catch (Exception e) {
            throw new IOException("解析考勤組數據失敗: " + e.getMessage(), e);
        }
    }


    /**
     * 執行同步操作。
     *
     * @param syncRequestList 包含員工 PINs 和考勤組 ID 的請求 DTO
     */
    public void syncEmployeesToGroup(List<SyncRequest> syncRequestList) throws IOException {
        // 1. 將 List<SyncRequest> 轉換為 JSON 數組字符串
        String requestBodyJson = objectMapper.writeValueAsString(syncRequestList);

        // 2. 執行 HTTP POST 請求
        // 假設您的 HttpClient 有一個方法 doGet/doPost，這裡使用 doPost
        String responseJson = HttpClientUtil.doPost(AppConstants.getSyncToMiddleAPI(), requestBodyJson);

        // 3. 解析響應，檢查同步結果
        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);

            String status = rootNode.get("status").asText();
            String msg = rootNode.get("msg").asText();

            // 如果狀態碼不是 200，則拋出異常
            if (!"200".equals(status)) {
                // 將 atten_middle 返回的錯誤信息拋出，以便 SyncGroupController 捕獲並顯示
                throw new IOException("同步到考勤機失敗: " + msg);
            }

            // 如果需要處理 content 中的詳細成功或失敗信息，可以在這裡添加邏輯

        } catch (IOException e) {
            // 重新拋出網絡或解析異常
            throw e;
        } catch (Exception e) {
            // 處理 JSON 處理過程中發生的其他異常
            throw new IOException("解析同步響應失敗: " + e.getMessage(), e);
        }
    }

}
