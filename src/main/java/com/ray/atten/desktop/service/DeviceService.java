package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.dto.DeviceRequest;
import com.ray.atten.desktop.dto.GlobalResponseBody;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.dto.SyncAttendanceCommendRequest;
import com.ray.atten.desktop.model.Device;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceService {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 初始化时确保 ObjectMapper 支持 Java8 时间
     */
    public DeviceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 获取设备列表
     */
    public List<Device> getDeviceList(DeviceRequest request) throws IOException {
        String requestJson;
        String responseJson;

        try {
            // 1. DTO 轉 JSON
            requestJson = objectMapper.writeValueAsString(request);

            System.out.println("Request Json ->> " + requestJson);
            // 1. 调用 HttpClientUtil.doGet
            responseJson = HttpClientUtil.doPost(AppConstants.getDeviceSearchAPI(), requestJson);

            // 3. 解析 Middle 服務返回的 GlobalResponseBody 結構
            // 假設 Middle 返回的是 GlobalResponseBody 封裝 Page<OaEmployee>

            GlobalResponseBody globalResponse = objectMapper.readValue(
                    responseJson,
                    GlobalResponseBody.class
            );

            // 2. 检查 GlobalResponseBody 风格的状态码
            if (!"200".equals(globalResponse.getStatus())) {
                throw new IOException("API 错误: " + globalResponse.getMsg());
            }

            Object content = globalResponse.getContent();
            if (content == null) {
                return new java.util.ArrayList<>(); // 如果 content 是空的，直接返回空列表而不是 null
            }

            String pageContentJson = objectMapper.writeValueAsString(content);
            return objectMapper.readValue(pageContentJson, new TypeReference<List<Device>>() {
            });
        } catch (Exception e) {
            throw new IOException("解析设备列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 新增或修改设备
     */
    public boolean saveOrUpdateDevice(Device device) {
        try {
            // 1. 对象转 JSON
            String body = objectMapper.writeValueAsString(device);
            // 2. 调用 HttpClientUtil.doPost
            String response = HttpClientUtil.doPost(AppConstants.getAddOrUpdateDeviceAPI(), body);
            // 3. 解析为 GlobalResponseBody
            GlobalResponseBody result = objectMapper.readValue(response, GlobalResponseBody.class);

            return "200".equals(result.getStatus());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 同步考勤数据
     * 假设后端接口 API_SYNC_ATTENDANCE 接收设备信息作为 Body
     */
    public boolean syncAttendanceData(List<String> sns) {
        try {
            // 1. 构建请求对象并填充固定内容
            SyncAttendanceCommendRequest syncRequest = new SyncAttendanceCommendRequest();
            syncRequest.setDeviceSns(sns);
            syncRequest.setCmd("DATA");
            syncRequest.setRecode("QUERY");
            syncRequest.setTable("ATTLOG");

            // 2. 序列化为 JSON 字符串
            String body = objectMapper.writeValueAsString(syncRequest);
            System.out.println("发送同步指令 ->> " + body);

            // 3. 调用 HttpClientUtil 发送请求
            String response = HttpClientUtil.doPost(AppConstants.getSyncAttendanceAPI(), body);

            // 4. 解析响应
            GlobalResponseBody result = objectMapper.readValue(response, GlobalResponseBody.class);

            return "200".equals(result.getStatus());
        } catch (Exception e) {
            System.err.println("同步指令下发失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}