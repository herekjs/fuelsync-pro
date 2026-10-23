package com.fuelsync.domain.tank.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.tank.dto.TankDto;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.tank.service.TankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tanks")
@RequiredArgsConstructor
public class TankController {

    private final TankService tankService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<TankDto.Response>>> getAll(
            @RequestParam(required = false) Tank.FuelType fuelType) {
        List<TankDto.Response> data = fuelType != null
                ? tankService.findByFuelType(fuelType)
                : tankService.findAll();
        return ResponseEntity.ok(CommonResponse.ok(data));
    }

    @GetMapping("/{tankId}")
    public ResponseEntity<CommonResponse<TankDto.Response>> getOne(@PathVariable Long tankId) {
        return ResponseEntity.ok(CommonResponse.ok(tankService.findById(tankId)));
    }

    @GetMapping("/{tankId}/daily-remaining")
    public ResponseEntity<CommonResponse<List<TankDto.DailyRemainingResponse>>> getDailyRemaining(
            @PathVariable Long tankId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusDays(14);
        LocalDate end = to != null ? to : LocalDate.now();
        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }
        return ResponseEntity.ok(CommonResponse.ok(tankService.findDailyRemaining(tankId, start, end)));
    }

    @GetMapping("/attention")
    public ResponseEntity<CommonResponse<List<TankDto.Response>>> getAttentionTanks() {
        return ResponseEntity.ok(CommonResponse.ok(tankService.findAllRequiringAttention()));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<TankDto.Response>> create(
            @Valid @RequestBody TankDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok("탱크가 등록되었습니다.", tankService.create(request)));
    }

    @PatchMapping("/{tankId}/sensor")
    public ResponseEntity<CommonResponse<TankDto.Response>> updateSensor(
            @PathVariable Long tankId,
            @RequestBody TankDto.SensorUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.ok(tankService.updateSensor(tankId, request)));
    }

    @PatchMapping("/{tankId}/inventory")
    public ResponseEntity<CommonResponse<TankDto.Response>> updateInventory(
            @PathVariable Long tankId,
            @Valid @RequestBody TankDto.InventoryUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.ok(
                "탱크 재고 정보가 반영되었습니다.",
                tankService.updateCapacityAndVolume(tankId, request)));
    }
}
