package com.fuelsync.domain.competitor.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.competitor.model.CompetitorStation;
import com.fuelsync.domain.competitor.service.CompetitorService;
import com.fuelsync.domain.tank.model.Tank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/competitor")
@RequiredArgsConstructor
public class CompetitorController {

    private final CompetitorService competitorService;

    /**
     * @param fuelType 유종 (GASOLINE, DIESEL, LPG, PREMIUM)
     * @param radius   반경 (km, 최대 10)
     */
    @GetMapping
    public ResponseEntity<CommonResponse<List<CompetitorStation>>> getAround(
            @RequestParam Tank.FuelType fuelType,
            @RequestParam(defaultValue = "3") int radius) {
        List<CompetitorStation> data = competitorService.findAround(fuelType, radius);
        return ResponseEntity.ok(CommonResponse.ok(data));
    }
}
