package com.ray.atten.desktop.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BaseModel implements Serializable {

    protected UUID uuid;

    protected LocalDateTime createTime;

    protected LocalDateTime updateTime;
}
