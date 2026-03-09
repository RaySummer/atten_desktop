package com.ray.atten.desktop.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 1. 注册支持 Java 8 时间类型的模块 (需确保 pom.xml 已添加 jackson-datatype-jsr310)
        mapper.registerModule(new JavaTimeModule());

        // 2. 禁用将日期序列化为时间戳，这样它会使用 ISO-8601 字符串 (如 2024-05-20T10:00:00)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. 忽略 JSON 中存在但 Java 类中没有的字段，防止后端增加字段后前端崩溃
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}