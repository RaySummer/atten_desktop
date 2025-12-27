package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.HttpClientUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CardTemplateService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取所有激活的模板列表 (供下拉菜单选择)
     * 对应后端: @GetMapping("/list-active")
     */
    public List<Map<String, String>> getActiveTemplates() throws IOException {
        String jsonResponse = HttpClientUtil.doGet(AppConstants.getCardTemplatesAPI(), null);

        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        // 后端返回的是 GlobalResponseBody，所以取 content 字段
        return objectMapper.convertValue(rootNode.path("content"),
                new TypeReference<List<Map<String, String>>>() {
                });
    }

    /**
     * 准备打印数据并获取票据
     * 对应后端: @PostMapping("/prepare-print")
     */
    public String createPrintTicket(String templateUuid, List<OaEmployee> employees) throws IOException {

        // 1. 核心优化点：在此处进行数据转换
        List<String> employeeIds = employees.stream().map(employee -> employee.getUuid() + "").collect(Collectors.toList());

        // 2. 构造后端要求的 PrintPayloadRequest 字段名
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("templateId", templateUuid);
        requestBody.put("employeeIds", employeeIds);

        String json = objectMapper.writeValueAsString(requestBody);

        // 3. 发送 POST 并直接返回 ticket 字符串
        return HttpClientUtil.doPost(AppConstants.getPreparePrintAPI(), json);
    }
}