package com.ray.atten.desktop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略 Spring Page 結構中我們不需要的字段，例如 pageable, sort, last, first 等
public class PageResponse<T> implements Serializable {

    private List<T> content;        // 數據內容列表
    private int totalPages;         // 總頁數
    private long totalElements;     // 總記錄數
    private int number;             // 當前頁碼 (JPA 從 0 開始)
    private int size;               // 每頁大小
    private boolean empty;          // 數據是否為空

}
