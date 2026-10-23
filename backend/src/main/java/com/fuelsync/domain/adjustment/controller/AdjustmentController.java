package com.fuelsync.domain.adjustment.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.adjustment.dto.AdjustmentDto;
import com.fuelsync.domain.adjustment.service.AdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/adjustments")
@RequiredArgsConstructor
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<AdjustmentDto.Response>>> getByPeriod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(CommonResponse.ok(adjustmentService.findByPeriod(start, end)));
    }

    @GetMapping("/tank/{tankId}")
    public ResponseEntity<CommonResponse<List<AdjustmentDto.Response>>> getByTank(@PathVariable Long tankId) {
        return ResponseEntity.ok(CommonResponse.ok(adjustmentService.findByTank(tankId)));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<AdjustmentDto.Response>> create(
            @Valid @RequestBody AdjustmentDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok("재고 보정이 등록되었습니다.", adjustmentService.create(request)));
    }
}
