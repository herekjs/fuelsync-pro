package com.fuelsync.domain.station.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.station.dto.StationInfoDto;
import com.fuelsync.domain.station.service.StationInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/station-info")
@RequiredArgsConstructor
public class StationInfoController {

    private final StationInfoService stationInfoService;

    @GetMapping
    public ResponseEntity<CommonResponse<StationInfoDto.Response>> get() {
        return stationInfoService.find()
                .map(data -> ResponseEntity.ok(CommonResponse.ok(data)))
                .orElseGet(() -> ResponseEntity.ok(CommonResponse.ok(null)));
    }

    @PutMapping
    public ResponseEntity<CommonResponse<StationInfoDto.Response>> upsert(
            @Valid @RequestBody StationInfoDto.Request request) {
        return ResponseEntity.ok(CommonResponse.ok("주유소 정보가 저장되었습니다.", stationInfoService.upsert(request)));
    }
}
