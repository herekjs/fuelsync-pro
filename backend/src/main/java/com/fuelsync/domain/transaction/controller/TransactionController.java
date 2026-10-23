package com.fuelsync.domain.transaction.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.excel.dto.ExcelUploadResult;
import com.fuelsync.domain.transaction.dto.TransactionDto;
import com.fuelsync.domain.transaction.service.ExcelUploadService;
import com.fuelsync.domain.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final ExcelUploadService excelUploadService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<TransactionDto.Response>>> getByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(CommonResponse.ok(transactionService.findByPeriod(from, to)));
    }

    @GetMapping("/recent")
    public ResponseEntity<CommonResponse<List<TransactionDto.Response>>> getRecentOneHour() {
        return ResponseEntity.ok(CommonResponse.ok(transactionService.findRecentOneHour()));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<TransactionDto.Response>> create(
            @Valid @RequestBody TransactionDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok("판매가 등록되었습니다.", transactionService.create(request)));
    }

    @PostMapping(value = "/upload-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<Map<String, Object>>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "incrementalOnly", defaultValue = "true") boolean incrementalOnly,
            @RequestParam(value = "alignOpeningFromExcel", defaultValue = "false") boolean alignOpeningFromExcel) {
        ExcelUploadResult result = excelUploadService.uploadExcel(file, incrementalOnly, alignOpeningFromExcel);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok("엑셀 업로드가 완료되었습니다.", Map.of(
                        "inserted", result.inserted(),
                        "batchId", result.batchId(),
                        "transactionInserted", result.transactionInserted(),
                        "stockInInserted", result.stockInInserted(),
                        "overwritten", result.overwrittenTransactions(),
                        "skippedDates", result.skippedDates(),
                        "adjustmentInserted", result.adjustmentInserted(),
                        "adjustmentSkipped", result.adjustmentSkipped(),
                        "adjustmentOverwrittenDeletes", result.adjustmentExcelDeletes()
                )));
    }
}
