package com.ray.atten.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Device extends BaseModel implements Serializable {

    // 考勤机序列号 (唯一标识)
    private String deviceSn;

    // 考勤机名称/别名
    private String alias;

    // 考勤机位置
    private String location;

    // 考勤机型号
    private String model;

    // 考勤机IP
    private String ipAddress;

    // 是否激活/启用同步
    private Boolean active;

    private List<UUID> companyUuids;

}
