package com.ray.atten.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceGroup extends BaseModel implements Serializable {

    private String groupName;
    private List<String> deviceSns;

    // 2. 【核心方法】提供拼接後的 deviceSns 字符串
    public String getDeviceSnsString() {
        if (deviceSns == null || deviceSns.isEmpty()) {
            return "";
        }
        // 使用逗號和空格拼接，例如："CJDE230960007, FAC1242101598"
        return deviceSns.stream().collect(Collectors.joining(", "));
    }

    // 3. 確保 ComboBox 顯示 groupName
    @Override
    public String toString() {
        return groupName;
    }
}
