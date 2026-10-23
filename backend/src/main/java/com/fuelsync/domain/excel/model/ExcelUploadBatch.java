package com.fuelsync.domain.excel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelUploadBatch {

    private Long batchId;
    private String fileName;
    private Integer totalRows;
    private Integer insertedCount;
    /** 판매·입고가 INSERT된 서로 다른 일자 수 */
    private Integer appliedDateCount;
    private LocalDateTime uploadedAt;
}
