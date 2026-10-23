package com.fuelsync.domain.dashboard.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.dashboard.dto.DashboardDto;
import com.fuelsync.domain.dashboard.dto.SalesStatsDto;
import com.fuelsync.domain.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<CommonResponse<DashboardDto.Response>> getDashboard() {
        return ResponseEntity.ok(CommonResponse.ok(dashboardService.getDashboard()));
    }

    @GetMapping("/stats")
    public ResponseEntity<CommonResponse<SalesStatsDto.Response>> getSalesStats(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(CommonResponse.ok(dashboardService.getSalesStats(startDate, endDate)));
    }
}
