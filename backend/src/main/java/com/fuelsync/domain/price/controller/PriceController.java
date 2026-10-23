package com.fuelsync.domain.price.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.price.dto.PriceDto;
import com.fuelsync.domain.price.service.PriceService;
import com.fuelsync.domain.tank.model.Tank;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/history")
    public ResponseEntity<CommonResponse<List<PriceDto.HistoryResponse>>> getHistory(
            @RequestParam Tank.FuelType fuelType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(CommonResponse.ok(priceService.findHistory(fuelType, start, end)));
    }

    @GetMapping("/timeline")
    public ResponseEntity<CommonResponse<List<PriceDto.TimelineResponse>>> getTimeline(
            @RequestParam(required = false) Tank.FuelType fuelType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end = to != null ? to : LocalDate.now();
        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }
        return ResponseEntity.ok(CommonResponse.ok(priceService.findTimeline(fuelType, start, end)));
    }

    @PostMapping("/history")
    public ResponseEntity<CommonResponse<PriceDto.HistoryResponse>> record(
            @Valid @RequestBody PriceDto.RecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok("가격이 기록되었습니다.", priceService.record(request)));
    }

    @PostMapping("/simulate")
    public ResponseEntity<CommonResponse<PriceDto.SimulateResponse>> simulate(
            @Valid @RequestBody PriceDto.SimulateRequest request) {
        return ResponseEntity.ok(CommonResponse.ok(priceService.simulate(request)));
    }
}
