package com.fuelsync.domain.dashboard.dto;

import lombok.Builder;

import java.util.List;

public class SalesStatsDto {

    @Builder
    public record Response(
            List<MonthlyStat> monthly,
            List<WeeklyStat> weekly,
            List<DailyStat> daily,
            List<DayOfWeekStat> dayOfWeek,
            List<DailyDiffStat> dailyDiff
    ) {}

    @Builder
    public record MonthlyStat(String month, double gasoline, double diesel) {}

    /** weekStart: 해당 주의 월요일 (yyyy-MM-dd) */
    @Builder
    public record WeeklyStat(String weekStart, double gasoline, double diesel) {}

    @Builder
    public record DailyStat(String date, double gasoline, double diesel) {}

    /** 엑셀 I·P열 판매차(L), 날짜별 최신 업로드 행 기준 */
    @Builder
    public record DailyDiffStat(String date, double gasoline, double diesel) {}

    /** dayOfWeek: MariaDB WEEKDAY — 0=월요일 … 6=일요일 */
    @Builder
    public record DayOfWeekStat(int dayOfWeek, double gasoline, double diesel) {}
}
