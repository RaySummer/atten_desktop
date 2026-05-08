package com.ray.atten.desktop.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeSync implements Serializable {

    private Integer fid;

    private String base64Data;

    private Integer base64Size;

    private String type;
}
