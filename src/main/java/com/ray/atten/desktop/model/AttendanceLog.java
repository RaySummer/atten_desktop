package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLog extends BaseModel implements Serializable {

    private String userPin;
    private String userName;
    private String deviceSn;
    private LocalDateTime verifyTime;
    private Integer status;
    private String verifyType;

}
