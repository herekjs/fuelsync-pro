package com.fuelsync.domain.station.dto;

import com.fuelsync.domain.station.model.StationInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StationInfoDto {

    public record Request(
            @Size(max = 100) String stationName,
            @Size(max = 200) String address,
            @DecimalMin("0") BigDecimal katechX,
            @DecimalMin("0") BigDecimal katechY,
            @Size(max = 20) String phone,
            @Size(max = 50) String brand
    ) {}

    public record Response(
            Long stationId,
            String stationName,
            String address,
            BigDecimal katechX,
            BigDecimal katechY,
            String phone,
            String brand,
            LocalDateTime updatedAt
    ) {
        public static Response from(StationInfo s) {
            return new Response(
                    s.getStationId(),
                    s.getStationName(),
                    s.getAddress(),
                    s.getKatechX(),
                    s.getKatechY(),
                    s.getPhone(),
                    s.getBrand(),
                    s.getUpdatedAt()
            );
        }
    }
}
