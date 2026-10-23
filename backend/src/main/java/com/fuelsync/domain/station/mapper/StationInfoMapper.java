package com.fuelsync.domain.station.mapper;

import com.fuelsync.domain.station.model.StationInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface StationInfoMapper {

    Optional<StationInfo> findFirst();

    void insert(StationInfo stationInfo);

    void update(StationInfo stationInfo);
}
