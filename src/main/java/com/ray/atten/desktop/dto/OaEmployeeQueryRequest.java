package com.ray.atten.desktop.dto;

import lombok.*;

import java.io.Serializable;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OaEmployeeQueryRequest implements Serializable {

    private String keyword;
    private String companyId;
    private String deptId;
    private Boolean inService;
    private int pageNum = 1;
    private int pageSize = 10;
    private String sortBy;
    private String sortOrder = "DESC";
    private Boolean hasFingerprint;
    private Boolean hasPhoto;
}
