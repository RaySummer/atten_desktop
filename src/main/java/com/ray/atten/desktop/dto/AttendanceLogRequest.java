package com.ray.atten.desktop.dto;

import lombok.*;

import java.io.Serializable;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLogRequest implements Serializable {

    private String keyword;
    private String startTime;
    private String endTime;
    private int pageNum = 1;
    private int pageSize = 10;
    private String sortBy;
    private String sortOrder = "DESC";

}
