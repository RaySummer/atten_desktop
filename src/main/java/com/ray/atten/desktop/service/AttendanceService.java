package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.dto.AttendanceLogRequest;
import com.ray.atten.desktop.dto.GlobalResponseBody;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.model.AttendanceLog;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class AttendanceService {

    @Autowired
    private ObjectMapper objectMapper;

    public AttendanceService() {
        this.objectMapper = new ObjectMapper();

        // 【核心修正】註冊 JSR310 模塊來處理 Java 8 日期時間
        this.objectMapper.registerModule(new JavaTimeModule());

    }

    public PageResponse<AttendanceLog> getAttendanceLogs(AttendanceLogRequest request) {
        try {
            // 1. 将请求对象转换为 JSON 字符串
            String jsonRequest = objectMapper.writeValueAsString(request);

            // 2. 调用 HttpClientUtil 发送 POST 请求
            // 假设你的 HttpClientUtil.postJson 方法接收 (url, jsonBody)
            String responseJson = HttpClientUtil.doPost(AppConstants.getAttendanceLogSearchAPI(), jsonRequest);

            GlobalResponseBody globalResponse = objectMapper.readValue(
                    responseJson,
                    GlobalResponseBody.class
            );

            if (!"200".equals(globalResponse.getStatus())) {
                return new PageResponse<>(); // 返回空結果
            }

            String pageContentJson = objectMapper.writeValueAsString(globalResponse.getContent());

            // b) 使用 TypeReference 進行泛型安全反序列化
            PageResponse<AttendanceLog> pageResponse = objectMapper.readValue(
                    pageContentJson,
                    new TypeReference<PageResponse<AttendanceLog>>() {
                    } // 注意這裡使用了 TypeReference
            );

            return pageResponse;

        } catch (IOException e) {
            // 在 JavaFX 應用中，遇到 IO 錯誤通常需要處理或拋出運行時異常
            throw new RuntimeException("無法連接到 Atten Middle 服務: " + e.getMessage(), e);
        }
    }
}
