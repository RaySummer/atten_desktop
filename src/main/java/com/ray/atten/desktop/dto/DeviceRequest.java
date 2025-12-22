package com.ray.atten.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRequest implements Serializable {

    private String deviceSn;

    private String alias;    // 考勤机名称
    private String location; // 考勤机位置
    private String model;    // 考勤机型号
    private String ipAddress; // 考勤机IP
    private Boolean active;  // 是否激活 (true/false)

}
