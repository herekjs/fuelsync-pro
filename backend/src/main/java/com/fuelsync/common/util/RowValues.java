package com.fuelsync.common.util;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** MyBatis가 {@code resultType="map"}으로 돌려주는 행 값을 도메인 타입으로 변환한다. */
public final class RowValues {

    private RowValues() {
    }

    /** null이면 null. 날짜/시각 타입 또는 {@code yyyy-MM-dd...} 문자열을 {@link LocalDate}로 변환한다. */
    public static LocalDate toLocalDate(Object rawDate) {
        if (rawDate == null) {
            return null;
        }
        if (rawDate instanceof LocalDate localDate) {
            return localDate;
        }
        if (rawDate instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (rawDate instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (rawDate instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }

        String text = rawDate.toString().trim();
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return LocalDate.parse(text);
    }

    /** null이면 null. 숫자 타입 또는 숫자 문자열을 {@link BigDecimal}로 변환한다. */
    public static BigDecimal toBigDecimal(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(rawValue.toString());
    }
}
