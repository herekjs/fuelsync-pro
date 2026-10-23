package com.fuelsync.domain.price.controller;

import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.price.model.SellingPrice;
import com.fuelsync.domain.price.service.SellingPriceService;
import com.fuelsync.domain.tank.model.Tank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/selling-price")
@RequiredArgsConstructor
public class SellingPriceController {

    private final SellingPriceService sellingPriceService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<SellingPrice>>> getAll() {
        return ResponseEntity.ok(CommonResponse.ok(sellingPriceService.findAll()));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<SellingPrice>> upsert(@RequestBody UpsertRequest req) {
        SellingPrice result = sellingPriceService.upsert(req.fuelType(), req.sellingPrice(), req.effectiveFrom());
        return ResponseEntity.ok(CommonResponse.ok("판매가가 저장되었습니다.", result));
    }

    public record UpsertRequest(
            @NotNull Tank.FuelType fuelType,
            @NotNull @DecimalMin("0.01") BigDecimal sellingPrice,
            LocalDate effectiveFrom
    ) {}
}
