package com.fuelsync.domain.transaction.service;

import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.domain.adjustment.service.AdjustmentService;
import com.fuelsync.domain.excel.mapper.ExcelUploadLogMapper;
import com.fuelsync.domain.excel.model.ExcelUploadBatch;
import com.fuelsync.domain.excel.model.ExcelUploadRow;
import com.fuelsync.domain.price.mapper.PriceHistoryMapper;
import com.fuelsync.domain.stockin.mapper.StockInMapper;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.transaction.mapper.TransactionMapper;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ExcelUploadServiceTest {

    private static final long GAS_TANK_ID = 1L;
    private static final long DIESEL_TANK_ID = 2L;

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

    @InjectMocks
    private ExcelUploadService excelUploadService;

    private Tank gasTank;
    private Tank dieselTank;

    @BeforeEach
    void setUp() {
        gasTank = tank(GAS_TANK_ID, Tank.FuelType.GASOLINE, new BigDecimal("100.00"));
        dieselTank = tank(DIESEL_TANK_ID, Tank.FuelType.DIESEL, new BigDecimal("1000.00"));

        when(tankMapper.findByFuelType("GASOLINE")).thenReturn(List.of(gasTank));
        when(tankMapper.findByFuelType("DIESEL")).thenReturn(List.of(dieselTank));
        when(tankMapper.findByIdForUpdate(GAS_TANK_ID)).thenReturn(Optional.of(gasTank));
        when(tankMapper.findByIdForUpdate(DIESEL_TANK_ID)).thenReturn(Optional.of(dieselTank));
        when(transactionMapper.countExcelByTankAndDate(anyLong(), any(LocalDate.class))).thenReturn(0);
        when(stockInMapper.countExcelByTankAndDate(anyLong(), any(LocalDate.class))).thenReturn(0);
        when(priceHistoryMapper.findLatestOurPriceByFuelTypeOnOrBefore(anyString(), any(LocalDate.class)))
                .thenReturn(new BigDecimal("1600.00"));
    }

    @Test
    void uploadExcel_success_setsInsertFlagsOnRows() throws Exception {
        doAnswer(inv -> {
            ExcelUploadBatch batch = inv.getArgument(0);
            batch.setBatchId(99L);
            return null;
        }).when(excelUploadLogMapper).insertBatch(any());

        LocalDate saleDate = LocalDate.of(2026, 6, 8);
        MultipartFile file = excelFile(saleDate, new BigDecimal("10"));

        excelUploadService.uploadExcel(file, true, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExcelUploadRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(excelUploadLogMapper).insertRows(rowsCaptor.capture());
        ExcelUploadRow row = rowsCaptor.getValue().get(0);
        assertFalse(Boolean.TRUE.equals(row.getGasStockInInserted()));
        assertTrue(Boolean.TRUE.equals(row.getGasSaleInserted()));
        assertFalse(Boolean.TRUE.equals(row.getDieselStockInInserted()));
        assertFalse(Boolean.TRUE.equals(row.getDieselSaleInserted()));
        verify(excelUploadLogMapper).updateBatchCounts(99L, 1, 1, 1);
    }

    @Test
    void uploadExcel_insufficientVolume_doesNotWriteToDatabase() throws Exception {
        LocalDate saleDate = LocalDate.of(2026, 6, 8);
        MultipartFile file = excelFile(saleDate, new BigDecimal("500"));

        FuelSyncException ex = assertThrows(
                FuelSyncException.class,
                () -> excelUploadService.uploadExcel(file, true, false));

        assertTrue(ex.getMessage().contains("잔량이 부족합니다"));
        assertTrue(ex.getMessage().contains("2026-06-08"));

        verify(excelUploadLogMapper, never()).insertBatch(any());
        verify(excelUploadLogMapper, never()).insertRows(any());
        verify(stockInMapper, never()).insert(any());
        verify(transactionMapper, never()).insertBatch(any());
        verify(adjustmentService, never()).createExcelIfAbsent(any());
        verify(tankMapper, never()).updateVolume(any());
        verify(tankMapper, never()).upsertDailyLevel(anyLong(), anyString(), any(), anyString());
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

    private MultipartFile excelFile(LocalDate saleDate, BigDecimal gasSaleLiter) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("data");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(saleDate.toString());
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
