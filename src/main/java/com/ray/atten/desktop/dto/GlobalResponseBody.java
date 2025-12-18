package com.ray.atten.desktop.dto;

import lombok.*;

import java.io.Serializable;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobalResponseBody implements Serializable {

    private String status;

    private String msg;

    private Object content;

}