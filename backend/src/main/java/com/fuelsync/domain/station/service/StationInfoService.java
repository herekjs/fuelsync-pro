package com.fuelsync.domain.station.service;

import com.fuelsync.domain.station.dto.StationInfoDto;
import com.fuelsync.domain.station.mapper.StationInfoMapper;
import com.fuelsync.domain.station.model.StationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StationInfoService {

    private final StationInfoMapper stationInfoMapper;

    public Optional<StationInfoDto.Response> find() {
        return stationInfoMapper.findFirst().map(StationInfoDto.Response::from);
    }

    @Transactional
    public StationInfoDto.Response upsert(StationInfoDto.Request request) {
        Optional<StationInfo> existing = stationInfoMapper.findFirst();

        if (existing.isPresent()) {
            StationInfo s = existing.get();
            applyRequest(s, request);
            stationInfoMapper.update(s);
            log.info("[Station] updated stationId={}", s.getStationId());
            return StationInfoDto.Response.from(s);
        } else {
            StationInfo s = new StationInfo();
            applyRequest(s, request);
            stationInfoMapper.insert(s);
            log.info("[Station] created stationId={}", s.getStationId());
            return StationInfoDto.Response.from(s);
        }
    }

    private void applyRequest(StationInfo s, StationInfoDto.Request req) {
        if (req.stationName() != null) s.setStationName(req.stationName());
        if (req.address()     != null) s.setAddress(req.address());
        if (req.katechX()     != null) s.setKatechX(req.katechX());
        if (req.katechY()     != null) s.setKatechY(req.katechY());
        if (req.phone()       != null) s.setPhone(req.phone());
        if (req.brand()       != null) s.setBrand(req.brand());
    }
}
