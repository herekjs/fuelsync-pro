package com.fuelsync.domain.price.dto;

import com.fuelsync.domain.tank.model.Tank;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private PriceDto.SimulateRequest request(String marginRate) {
        return new PriceDto.SimulateRequest(Tank.FuelType.GASOLINE, new BigDecimal("1500"), new BigDecimal(marginRate), null);
    }

    @Test
    void marginRate_below100_isValid() {
        assertTrue(validator.validate(request("20")).isEmpty());
        assertTrue(validator.validate(request("99.99")).isEmpty());
        assertTrue(validator.validate(request("0.01")).isEmpty());
    }

    @Test
    void marginRate_100OrMore_isRejectedWithMessage() {
        for (String rate : new String[]{"100", "100.5", "150"}) {
            var violations = validator.validate(request(rate));
            assertEquals(1, violations.size(), rate);
            assertEquals("마진율은 100 미만이어야 합니다.", violations.iterator().next().getMessage());
        }
    }

    @Test
    void marginRate_zeroOrNegative_isRejected() {
        assertEquals(1, validator.validate(request("0")).size());
        assertEquals(1, validator.validate(request("-5")).size());
    }
}
