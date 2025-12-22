package com.ray.atten.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncAttendanceCommendRequest implements Serializable {

    private List<String> deviceSns;
    private String cmd;
    private String recode;
    private String table;

}
