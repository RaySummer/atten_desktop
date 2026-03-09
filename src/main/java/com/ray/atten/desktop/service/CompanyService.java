package com.ray.atten.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.atten.desktop.model.Company;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyService {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取所有公司列表 (用于下拉勾选)
     */
    public List<Company> getAllCompanies() throws Exception {
        String url = AppConstants.getCompanyListAPI();
        String response = HttpClientUtil.doGet(url, null);

        JsonNode rootNode = objectMapper.readTree(response);
        // 后端返回的是 GlobalResponseBody，所以取 content 字段
        return objectMapper.convertValue(rootNode.path("content"),
                new TypeReference<List<Company>>() {
                });
    }

    /**
     * 创建公司
     */
    public void createCompany(Company company) throws Exception {
        String url = AppConstants.getAddCompanyAPI();
        String jsonBody = objectMapper.writeValueAsString(company);
        HttpClientUtil.doPost(url, jsonBody);
    }

}