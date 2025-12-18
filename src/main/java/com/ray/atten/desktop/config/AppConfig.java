package com.ray.atten.desktop.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        // 掃描應用程序的主包，Spring 將從這裡開始尋找 Bean。
        "com.ray.atten.desktop"
})
public class AppConfig {


}
