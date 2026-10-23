package com.fuelsync.domain.excel.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.excel.dto.ExcelUploadLogDto;
import com.fuelsync.domain.excel.service.ExcelUploadLogService;
import com.fuelsync.domain.maintenance.service.OperationalDataPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/excel-uploads")
@RequiredArgsConstructor
public class ExcelUploadLogController {

    private final ExcelUploadLogService excelUploadLogService;
    private final OperationalDataPurgeService operationalDataPurgeService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<ExcelUploadLogDto.BatchResponse>>> listBatches(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(CommonResponse.ok(excelUploadLogService.findBatches(from, to)));
    }

    @GetMapping("/{batchId}/rows")
    public ResponseEntity<CommonResponse<List<ExcelUploadLogDto.RowResponse>>> listRows(
            @PathVariable Long batchId) {
        return ResponseEntity.ok(CommonResponse.ok(excelUploadLogService.findRowsByBatchId(batchId)));
    }

    @DeleteMapping("/operational-data")
    public ResponseEntity<CommonResponse<Void>> purgeOperationalData(Authentication authentication) {
        operationalDataPurgeService.purgeUploadAndOperationalTables(authentication.getName());
        return ResponseEntity.ok(CommonResponse.ok(null));
    }
}
