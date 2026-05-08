package com.ray.atten.desktop.dto;

import com.ray.atten.desktop.model.EmployeeSync;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequest implements Serializable {

    private String pin;

    private String name;

    private String deviceSn;

    private List<EmployeeSync> fingerFidList = new ArrayList<>();

}
