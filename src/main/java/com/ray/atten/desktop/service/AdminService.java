package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.model.AdminUser;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.HttpClientUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final ObjectMapper objectMapper; // 关键：找不到字段不报错;

    public AdminService() {
        this.objectMapper = new ObjectMapper();

        // 1. 注册 JavaTimeModule 以支持 LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());

        // 2. 建议：禁用将日期写为时间戳的特性，使其以标准 ISO 格式处理
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. 之前提到的忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 获取所有管理员列表
     */
    public List<AdminUser> getAllAdmins(String query) throws Exception {
        String url = AppConstants.getAdminListAPI();
        if (query != null && !query.isEmpty()) {
            url += "?username=" + query;
        }
        String response = HttpClientUtil.doGet(url, null);

        JsonNode rootNode = objectMapper.readTree(response);
        // 后端返回的是 GlobalResponseBody，所以取 content 字段
        return objectMapper.convertValue(rootNode.path("content"),
                new TypeReference<List<AdminUser>>() {
                });
    }

    /**
     * 创建新管理员
     */
    public void createAdmin(AdminUser vo) throws Exception {
        String url = AppConstants.getAddAdminAPI();
        String jsonBody = objectMapper.writeValueAsString(vo);
        HttpClientUtil.doPost(url, jsonBody);
    }

    /**
     * 更新管理员信息 (根据 uuid)
     */
    public void updateAdmin(AdminUser vo) throws Exception {
        if (vo.getUuid() == null) throw new RuntimeException("更新失败：缺少唯一标识 UUID");

        String url = AppConstants.getUpdateAdminAPI(vo.getUuid().toString());
        String jsonBody = objectMapper.writeValueAsString(vo);
        HttpClientUtil.doPut(url, jsonBody);
    }

    /**
     * 删除管理员
     */
    public void deleteAdmin(String uuid) throws Exception {
        String url = AppConstants.getDelCompanyAPI(uuid);
        HttpClientUtil.doDelete(url);
    }
}
