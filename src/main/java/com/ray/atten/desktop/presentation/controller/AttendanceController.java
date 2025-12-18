package com.ray.atten.desktop.presentation.controller;

import org.springframework.stereotype.Component;
import javafx.fxml.FXML;

@Component
public class AttendanceController {

    @FXML
    public void initialize() {
        // 可以在這裡初始化一些臨時數據
        System.out.println("考勤統計頁面已載入");
    }
}