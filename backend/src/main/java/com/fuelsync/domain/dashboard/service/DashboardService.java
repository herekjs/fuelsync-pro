package com.fuelsync.domain.dashboard.service;

import com.fuelsync.domain.dashboard.dto.DashboardDto;
import com.fuelsync.domain.dashboard.dto.SalesStatsDto;
import com.fuelsync.domain.excel.mapper.ExcelUploadLogMapper;
import com.fuelsync.domain.stockin.mapper.StockInMapper;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.transaction.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RUNWAY_DAYS = 3;

    /** 소진예상(일) 및 기준일·마지막 판매일 */
    private record StockRunwayDaysInfo(Double days, LocalDate baselineDate, LocalDate lastSaleDate) {}

    private final TankMapper tankMapper;
    private final TransactionMapper transactionMapper;
    private final StockInMapper stockInMapper;
    private final ExcelUploadLogMapper excelUploadLogMapper;

    public DashboardDto.Response getDashboard() {
        List<Tank> tanks = tankMapper.findAll();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);

        List<DashboardDto.TankSummary> tankSummaries = tanks.stream()
                .map(tank -> buildTankSummary(tank, startOfDay))
                .toList();

        DashboardDto.TodaySummary todaySummary = buildTodaySummary(tankSummaries, tanks);
        List<DashboardDto.FuelTypeSummary> fuelTypeSummaries = buildFuelTypeSummaries(tanks);

        return DashboardDto.Response.builder()
                .tanks(tankSummaries)
                .today(todaySummary)
                .fuelTypeSummaries(fuelTypeSummaries)
                .build();
    }

    public SalesStatsDto.Response getSalesStats(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> monthlyRows = transactionMapper.findMonthlyStats(startDate, endDate);
        List<Map<String, Object>> weeklyRows = transactionMapper.findWeeklyStats(startDate, endDate);
        List<Map<String, Object>> dailyRows = transactionMapper.findDailyStats(startDate, endDate);
        List<Map<String, Object>> dayOfWeekRows = transactionMapper.findDayOfWeekAvgStats(startDate, endDate);
        List<Map<String, Object>> dailyDiffRows = excelUploadLogMapper.findDailyDiffStats(startDate, endDate);

        List<SalesStatsDto.MonthlyStat> monthly = pivotPeriodStats(monthlyRows);
        List<SalesStatsDto.WeeklyStat> weekly = pivotWeeklyStats(weeklyRows);
        List<SalesStatsDto.DailyStat> daily = pivotDailyStats(dailyRows);
        List<SalesStatsDto.DayOfWeekStat> dayOfWeek = pivotDayOfWeekStats(dayOfWeekRows);
        List<SalesStatsDto.DailyDiffStat> dailyDiff = pivotDailyDiffStats(dailyDiffRows);

        return SalesStatsDto.Response.builder()
                .monthly(monthly)
                .weekly(weekly)
                .daily(daily)
                .dayOfWeek(dayOfWeek)
                .dailyDiff(dailyDiff)
                .build();
    }

    private List<SalesStatsDto.MonthlyStat> pivotPeriodStats(List<Map<String, Object>> rows) {
        TreeSet<String> keys = new TreeSet<>();
        Map<String, BigDecimal> gas = new HashMap<>();
        Map<String, BigDecimal> die = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String pk = periodKeyToString(row.get("period_key"));
            if (pk.isEmpty()) {
                continue;
            }
            keys.add(pk);
            String ft = String.valueOf(row.get("fuel_type"));
            BigDecimal lit = toBigDecimalValue(row.get("total_liter"));
            if ("GASOLINE".equals(ft)) {
                gas.put(pk, lit);
            } else if ("DIESEL".equals(ft)) {
                die.put(pk, lit);
            }
        }
        List<SalesStatsDto.MonthlyStat> out = new ArrayList<>();
        for (String k : keys) {
            out.add(SalesStatsDto.MonthlyStat.builder()
                    .month(k)
                    .gasoline(gas.getOrDefault(k, BigDecimal.ZERO).doubleValue())
                    .diesel(die.getOrDefault(k, BigDecimal.ZERO).doubleValue())
                    .build());
        }
        return out;
    }

    private List<SalesStatsDto.WeeklyStat> pivotWeeklyStats(List<Map<String, Object>> rows) {
        TreeSet<String> keys = new TreeSet<>();
        Map<String, BigDecimal> gas = new HashMap<>();
        Map<String, BigDecimal> die = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String pk = periodKeyToString(row.get("period_key"));
            if (pk.isEmpty()) {
                continue;
            }
            keys.add(pk);
            String ft = String.valueOf(row.get("fuel_type"));
            BigDecimal lit = toBigDecimalValue(row.get("total_liter"));
            if ("GASOLINE".equals(ft)) {
                gas.put(pk, lit);
            } else if ("DIESEL".equals(ft)) {
                die.put(pk, lit);
            }
        }
        List<SalesStatsDto.WeeklyStat> out = new ArrayList<>();
        for (String k : keys) {
            out.add(SalesStatsDto.WeeklyStat.builder()
                    .weekStart(k)
                    .gasoline(gas.getOrDefault(k, BigDecimal.ZERO).doubleValue())
                    .diesel(die.getOrDefault(k, BigDecimal.ZERO).doubleValue())
                    .build());
        }
        return out;
    }

    private List<SalesStatsDto.DailyStat> pivotDailyStats(List<Map<String, Object>> rows) {
        TreeSet<String> keys = new TreeSet<>();
        Map<String, BigDecimal> gas = new HashMap<>();
        Map<String, BigDecimal> die = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String pk = periodKeyToString(row.get("period_key"));
            if (pk.isEmpty()) {
                continue;
            }
            keys.add(pk);
            String ft = String.valueOf(row.get("fuel_type"));
            BigDecimal lit = toBigDecimalValue(row.get("total_liter"));
            if ("GASOLINE".equals(ft)) {
                gas.put(pk, lit);
            } else if ("DIESEL".equals(ft)) {
                die.put(pk, lit);
            }
        }
        List<SalesStatsDto.DailyStat> out = new ArrayList<>();
        for (String k : keys) {
            out.add(SalesStatsDto.DailyStat.builder()
                    .date(k)
                    .gasoline(gas.getOrDefault(k, BigDecimal.ZERO).doubleValue())
                    .diesel(die.getOrDefault(k, BigDecimal.ZERO).doubleValue())
                    .build());
        }
        return out;
    }

    private List<SalesStatsDto.DailyDiffStat> pivotDailyDiffStats(List<Map<String, Object>> rows) {
        List<SalesStatsDto.DailyDiffStat> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String pk = periodKeyToString(row.get("period_key"));
            if (pk.isEmpty()) {
                continue;
            }
            out.add(SalesStatsDto.DailyDiffStat.builder()
                    .date(pk)
                    .gasoline(toBigDecimalValue(row.get("gasoline_diff")).doubleValue())
                    .diesel(toBigDecimalValue(row.get("diesel_diff")).doubleValue())
                    .build());
        }
        return out;
    }

    /** WEEKDAY 0(월)~6(일) 각각 휘발유·경유 일평균(L); 데이터 없는 요일은 0 */
    private List<SalesStatsDto.DayOfWeekStat> pivotDayOfWeekStats(List<Map<String, Object>> rows) {
        BigDecimal[] gas = new BigDecimal[7];
        BigDecimal[] die = new BigDecimal[7];
        for (int i = 0; i < 7; i++) {
            gas[i] = BigDecimal.ZERO;
            die[i] = BigDecimal.ZERO;
        }
        for (Map<String, Object> row : rows) {
            int dow = toIntDayOfWeek(row.get("dow"));
            if (dow < 0 || dow > 6) {
                continue;
            }
            String ft = String.valueOf(row.get("fuel_type"));
            BigDecimal lit = toBigDecimalValue(row.get("avg_liter"));
            if ("GASOLINE".equals(ft)) {
                gas[dow] = lit;
            } else if ("DIESEL".equals(ft)) {
                die[dow] = lit;
            }
        }
        List<SalesStatsDto.DayOfWeekStat> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            out.add(SalesStatsDto.DayOfWeekStat.builder()
                    .dayOfWeek(i)
                    .gasoline(gas[i].doubleValue())
                    .diesel(die[i].doubleValue())
                    .build());
        }
        return out;
    }

    private int toIntDayOfWeek(Object value) {
        if (value == null) {
            return -1;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String periodKeyToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (value instanceof LocalDate ld) {
            return ld.toString();
        }
        return value.toString().trim();
    }

    private BigDecimal toBigDecimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    private DashboardDto.TankSummary buildTankSummary(Tank tank, LocalDateTime startOfDay) {
        Map<String, Object> todayData = transactionMapper.findTodaySummaryByTank(tank.getTankId(), startOfDay);
        BigDecimal todaySoldLiter = toBigDecimal(todayData, "soldLiter");
        BigDecimal todayRevenue   = toBigDecimal(todayData, "revenue");

        Double runwayHours = calculateStockRunway(tank);
        StockRunwayDaysInfo runwayDaysInfo = computeStockRunwayDaysInfo(tank);

        return DashboardDto.TankSummary.builder()
                .tankId(tank.getTankId())
                .tankName(tank.getTankName())
                .fuelType(tank.getFuelType().name())
                .maxCapacity(tank.getMaxCapacity())
                .currentVolume(tank.getCurrentVolume())
                .fillRatio(tank.getFillRatio())
                .temperature(tank.getTemperature())
                .waterLevel(tank.getWaterLevel())
                .status(tank.getStatus().name())
                .stockRunwayHours(runwayHours)
                .stockRunwayDays(runwayDaysInfo.days())
                .stockRunwayBaselineDate(runwayDaysInfo.baselineDate())
                .lastSaleDate(runwayDaysInfo.lastSaleDate())
                .todaySoldLiter(todaySoldLiter)
                .todayRevenue(todayRevenue)
                .build();
    }

    /**
     * Stock Runway: 최근 3일 시간대별 평균 판매량 기반 소진 예상 시간 계산
     */
    private Double calculateStockRunway(Tank tank) {
        LocalDateTime since = LocalDateTime.now().minusDays(RUNWAY_DAYS);
        List<Map<String, Object>> hourlyData = transactionMapper.findHourlyLiterSince(tank.getTankId(), since);

        if (hourlyData.isEmpty()) return null;

        double totalLiters = hourlyData.stream()
                .mapToDouble(row -> ((Number) row.get("totalLiter")).doubleValue())
                .sum();

        double avgLiterPerHour = totalLiters / (RUNWAY_DAYS * 24.0);
        if (avgLiterPerHour == 0) return null;

        return tank.getCurrentVolume().doubleValue() / avgLiterPerHour;
    }

    /**
     * 최근 입고일 0시 이후 판매 합계와 입고일~오늘 달력 일수(양 끝 포함, 최소 1)로 일평균 판매량을 구해 소진 예상 일수·기준일·해당 구간 마지막 판매일 계산
     */
    private StockRunwayDaysInfo computeStockRunwayDaysInfo(Tank tank) {
        LocalDate lastInDate = stockInMapper.findLatestInDateByTankId(tank.getTankId());
        if (lastInDate == null) {
            return new StockRunwayDaysInfo(null, null, null);
        }

        LocalDateTime since = lastInDate.atStartOfDay();
        LocalDate lastSaleDate = transactionMapper.findLastSaleDateSince(tank.getTankId(), since);
        BigDecimal totalSold = transactionMapper.sumLiterSince(tank.getTankId(), since);
        if (totalSold == null || totalSold.compareTo(BigDecimal.ZERO) == 0) {
            return new StockRunwayDaysInfo(null, lastInDate, lastSaleDate);
        }

        LocalDate today = LocalDate.now();
        long inclusiveDays = ChronoUnit.DAYS.between(lastInDate, today) + 1;
        double periodDays = Math.max(1, inclusiveDays);
        double avgDaily = totalSold.doubleValue() / periodDays;
        if (avgDaily == 0) {
            return new StockRunwayDaysInfo(null, lastInDate, lastSaleDate);
        }

        double days = tank.getCurrentVolume().doubleValue() / avgDaily;
        return new StockRunwayDaysInfo(days, lastInDate, lastSaleDate);
    }

    private DashboardDto.TodaySummary buildTodaySummary(
            List<DashboardDto.TankSummary> tankSummaries, List<Tank> tanks) {
        BigDecimal totalRevenue = tankSummaries.stream()
                .map(DashboardDto.TankSummary::todayRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSoldLiter = tankSummaries.stream()
                .map(DashboardDto.TankSummary::todaySoldLiter)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long warningCount = tanks.stream().filter(t -> t.getStatus() == Tank.TankStatus.WARNING).count();
        long dangerCount  = tanks.stream().filter(t -> t.getStatus() == Tank.TankStatus.DANGER).count();

        BigDecimal avgBuyPrice = stockInMapper.findOverallAvgBuyPriceSince(LocalDate.now().minusMonths(1));
        BigDecimal estimatedMargin = avgBuyPrice != null
                ? totalRevenue.subtract(totalSoldLiter.multiply(avgBuyPrice).setScale(2, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

        return DashboardDto.TodaySummary.builder()
                .totalRevenue(totalRevenue)
                .totalSoldLiter(totalSoldLiter)
                .estimatedMargin(estimatedMargin)
                .tankCount(tanks.size())
                .warningCount((int) warningCount)
                .dangerCount((int) dangerCount)
                .build();
    }

    private List<DashboardDto.FuelTypeSummary> buildFuelTypeSummaries(List<Tank> tanks) {
        Map<Tank.FuelType, List<Tank>> grouped = tanks.stream()
                .collect(Collectors.groupingBy(Tank::getFuelType));

        return grouped.entrySet().stream().map(entry -> {
            List<Tank> group = entry.getValue();
            BigDecimal totalVolume   = group.stream().map(Tank::getCurrentVolume).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCapacity = group.stream().map(Tank::getMaxCapacity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fillRatio = totalCapacity.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : totalVolume.divide(totalCapacity, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            return DashboardDto.FuelTypeSummary.builder()
                    .fuelType(entry.getKey().name())
                    .totalVolume(totalVolume)
                    .totalCapacity(totalCapacity)
                    .fillRatio(fillRatio)
                    .tankCount(group.size())
                    .build();
        }).toList();
    }

    private BigDecimal toBigDecimal(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return BigDecimal.ZERO;
        return new BigDecimal(map.get(key).toString());
    }
}
