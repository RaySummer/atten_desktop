package com.ray.atten.desktop.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OaEmployee implements Serializable {

    private Long id;
    private String pin;
    private String name;
    private String company;
    private String dept;
    private Boolean inService;

    // 確保這裡的屬性名和類型與 Middle 服務返回的 JSON 結構完全一致
    private LocalDateTime entryDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private String fingerprint;

    private Integer fingerSize;

    private String photoBase64;

    private Integer photoSize;

    private String deviceSn;

    private Integer fid;

}
