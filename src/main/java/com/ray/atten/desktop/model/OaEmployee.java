package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OaEmployee extends BaseModel implements Serializable {

    private String pin;
    private String name;
    private String company;
    private String dept;
    private String avatar;
    private String post;
    private Boolean inService;
    private String officeLocation;
    private LocalDateTime entryDate;

    // 所有的生物识别数据（type="finger" 指纹, type="photo" 人像）
    private List<EmployeeSync> syncList = new ArrayList<>();

    /**
     * 内部类：用于 UI 下拉框显示状态
     */
    @Data
    @AllArgsConstructor
    public static class FingerStatus {
        private int fid;
        private boolean recorded;

        @Override
        public String toString() {
            // 这里决定了 ComboBox 每一行显示的文字
            return "指纹 " + (fid + 1) + (recorded ? " [已录入]" : " [未录入]");
        }
    }

    /**
     * 获取 10 个手指的状态列表，供 ComboBox 使用
     */
    @JsonIgnore
    public List<FingerStatus> getFingerStatuses() {
        List<FingerStatus> statuses = new ArrayList<>();

        // 提取 syncList 中已经存在的指纹 ID 集合
        Set<Integer> recordedFids = syncList.stream()
                .filter(s -> "finger".equals(s.getType()) && s.getFid() != null)
                .map(EmployeeSync::getFid)
                .collect(Collectors.toSet());

        // 构造 0-9 号手指状态
        for (int i = 0; i < 10; i++) {
            statuses.add(new FingerStatus(i, recordedFids.contains(i)));
        }
        return statuses;
    }

    /**
     * 获取人像照片的 Base64
     */
    @JsonIgnore
    public String getPhotoBase64() {
        if (syncList == null) return null;
        return syncList.stream()
                .filter(s -> "photo".equals(s.getType()))
                .map(EmployeeSync::getBase64Data)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据手指编号获取指纹 Base64
     */
    @JsonIgnore
    public String getFingerprintBase64(int fid) {
        if (syncList == null) return null;
        return syncList.stream()
                .filter(s -> "finger".equals(s.getType()) && s.getFid() != null && s.getFid() == fid)
                .map(EmployeeSync::getBase64Data)
                .findFirst()
                .orElse(null);
    }

    // JavaFX 选中属性
    @JsonIgnore
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    @JsonProperty("selected")
    public BooleanProperty selectedProperty() { return selected; }

    @JsonProperty("selected")
    public boolean isSelected() { return selected.get(); }

    public void setSelected(boolean selected) { this.selected.set(selected); }
}