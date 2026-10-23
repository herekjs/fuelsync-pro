package com.fuelsync.domain.transaction.service;

import com.fuelsync.domain.adjustment.service.AdjustmentService;
import com.fuelsync.domain.excel.mapper.ExcelUploadLogMapper;
import com.fuelsync.domain.excel.model.ExcelUploadBatch;
import com.fuelsync.domain.price.mapper.PriceHistoryMapper;
import com.fuelsync.domain.stockin.mapper.StockInMapper;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.transaction.mapper.TransactionMapper;
import com.fuelsync.websocket.StockEventPublisher;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 엑셀 "덮어쓰기" 모드: 지워질 기존 엑셀 기록이 잔량에 준 영향을 되돌리고 계산하는지 확인한다. */
@ExtendWith(MockitoExtension.class)
class ExcelUploadOverwriteTest {

    private static final long GAS_TANK_ID = 1L;
    private static final long DIESEL_TANK_ID = 2L;
    private static final LocalDate SALE_DATE = LocalDate.of(2026, 6, 8);

    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private TankMapper tankMapper;
    @Mock
    private PriceHistoryMapper priceHistoryMapper;
    @Mock
    private ExcelUploadLogMapper excelUploadLogMapper;
    @Mock
    private StockInMapper stockInMapper;
    @Mock
    private AdjustmentService adjustmentService;
    @Mock
    private StockEventPublisher stockEventPublisher;

    @InjectMocks
    private ExcelUploadService excelUploadService;

    private Tank gasTank;
    private Tank dieselTank;

    @BeforeEach
    void setUp() {
        // 첫 업로드(판매 30L)가 반영된 뒤의 잔량: 100 - 30 = 70
        gasTank = tank(GAS_TANK_ID, Tank.FuelType.GASOLINE, new BigDecimal("70.00"));
        dieselTank = tank(DIESEL_TANK_ID, Tank.FuelType.DIESEL, new BigDecimal("1000.00"));

        when(tankMapper.findByFuelType("GASOLINE")).thenReturn(List.of(gasTank));
        when(tankMapper.findByFuelType("DIESEL")).thenReturn(List.of(dieselTank));
        when(tankMapper.findByIdForUpdate(GAS_TANK_ID)).thenReturn(Optional.of(gasTank));
        when(tankMapper.findByIdForUpdate(DIESEL_TANK_ID)).thenReturn(Optional.of(dieselTank));
        when(priceHistoryMapper.findLatestOurPriceByFuelTypeOnOrBefore(any(), any(LocalDate.class)))
                .thenReturn(new BigDecimal("1600.00"));
        doAnswer(inv -> {
            ExcelUploadBatch batch = inv.getArgument(0);
            batch.setBatchId(99L);
            return null;
        }).when(excelUploadLogMapper).insertBatch(any());
    }

    @Test
    void overwriteSameSale_doesNotDoubleCountExistingExcelSale() throws Exception {
        when(transactionMapper.sumExcelLiterByTankAndDates(eq(GAS_TANK_ID), any()))
                .thenReturn(new BigDecimal("30.000"));

        excelUploadService.uploadExcel(excelFile(new BigDecimal("30")), false, false);

        // 되돌리면 100, 같은 판매 30L을 다시 반영하면 70. 되돌리지 않으면 40이 된다.
        assertEquals(0, new BigDecimal("70.00").compareTo(gasTank.getCurrentVolume()),
                "gas volume was " + gasTank.getCurrentVolume());
        assertEquals(0, new BigDecimal("1000.00").compareTo(dieselTank.getCurrentVolume()));
        verify(stockEventPublisher).publishTankChange(gasTank);
    }

    @Test
    void overwriteWithoutExistingExcelData_leavesVolumeMath_unchanged() throws Exception {
        // 합계 조회가 null/0 이면 되돌릴 것이 없으므로 70 - 30 = 40
        excelUploadService.uploadExcel(excelFile(new BigDecimal("30")), false, false);

        assertEquals(0, new BigDecimal("40.00").compareTo(gasTank.getCurrentVolume()));
    }

    @Test
    void overwriteReversesExistingStockInAndAdjustment() throws Exception {
        // 기존 엑셀 입고 50L, 조정 +5L 이 잔량에 반영돼 있었다면 되돌리면 70 - 50 - 5 = 15
        when(stockInMapper.sumExcelVolumeByTankAndDates(eq(GAS_TANK_ID), any()))
                .thenReturn(new BigDecimal("50.00"));
        when(adjustmentService.sumExcelDiffByTankAndDates(eq(GAS_TANK_ID), any()))
                .thenReturn(new BigDecimal("5.00"));

        // 새 시트에 판매 10L: 15 - 10 = 5
        excelUploadService.uploadExcel(excelFile(new BigDecimal("10")), false, false);

        assertEquals(0, new BigDecimal("5.00").compareTo(gasTank.getCurrentVolume()),
                "gas volume was " + gasTank.getCurrentVolume());
    }

    private Tank tank(Long tankId, Tank.FuelType fuelType, BigDecimal currentVolume) {
        Tank tank = Tank.builder()
                .tankName(fuelType.name())
                .fuelType(fuelType)
                .maxCapacity(new BigDecimal("50000.00"))
                .currentVolume(currentVolume)
                .thresholdWarning(20)
                .thresholdDanger(10)
                .build();
        tank.setTankId(tankId);
        return tank;
    }

    private MultipartFile excelFile(BigDecimal gasSaleLiter) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("data");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(SALE_DATE.toString());
            row.createCell(3).setCellValue(gasSaleLiter.doubleValue());
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
