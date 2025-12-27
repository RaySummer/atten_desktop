package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OaEmployee implements Serializable {

    private UUID uuid;
    private String pin;
    private String name;
    private String company;
    private String dept;
    private String avatar;
    private String post;
    private Boolean inService;
    private String officeLocation;

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
