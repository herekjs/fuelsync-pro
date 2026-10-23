package com.fuelsync.domain.stockin.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.stockin.dto.StockInDto;
import com.fuelsync.domain.stockin.service.StockInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-in")
@RequiredArgsConstructor
public class StockInController {

    private final StockInService stockInService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<StockInDto.Response>>> getByPeriod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(CommonResponse.ok(stockInService.findByPeriod(start, end)));
    }

    @GetMapping("/tank/{tankId}")
    public ResponseEntity<CommonResponse<List<StockInDto.Response>>> getByTank(@PathVariable Long tankId) {
        return ResponseEntity.ok(CommonResponse.ok(stockInService.findByTank(tankId)));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<StockInDto.Response>> create(
            @Valid @RequestBody StockInDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok("입고가 등록되었습니다.", stockInService.create(request)));
    }
}
