package com.ray.atten.desktop.dto;

import lombok.*;

import java.io.Serializable;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequest implements Serializable {

    private String pin;

    private String name;

    private String fingerprint;

    private Integer fingerSize;

    private String photoBase64;

    private Integer photoSize;

    private String deviceSn;

}
