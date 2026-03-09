package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AdminUser extends BaseModel {

    private String username;

    private String password;

    @JsonProperty("superAdmin")
    private Boolean isSuperAdmin;

    private List<Company> managedCompanies;

}
