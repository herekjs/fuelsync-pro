package com.fuelsync.domain.transaction.service;

import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.domain.adjustment.dto.AdjustmentDto;
import com.fuelsync.domain.adjustment.service.AdjustmentService;
import com.fuelsync.domain.excel.dto.ExcelUploadResult;
import com.fuelsync.domain.excel.mapper.ExcelUploadLogMapper;
import com.fuelsync.domain.excel.model.ExcelUploadBatch;
import com.fuelsync.domain.excel.model.ExcelUploadRow;
import com.fuelsync.domain.price.mapper.PriceHistoryMapper;
import com.fuelsync.domain.stockin.mapper.StockInMapper;
import com.fuelsync.domain.stockin.model.StockIn;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.transaction.mapper.TransactionMapper;
import com.fuelsync.domain.transaction.model.Transaction;
import com.fuelsync.websocket.StockEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelUploadService {

    /**
     * 재고 조정 컬럼(0-based 열 인덱스).
     * W(휘발유)·Y(경유) = 마감 잔여량 — 있으면 실측 마감, 없으면 행 시작 잔량+입고−판매로 계산 마감을 저장한다.
     * X(휘발유)·Z(경유) = 사용자 차이값, 계산에는 사용하지 않고 로그에만 보관한다.
     * 행은 날짜·시트 순(rowNo)으로 정렬되어 처리된다.
     */
    private static final int COL_ADJ_GAS_PHYSICAL = 22;
    private static final int COL_ADJ_GAS_DIFF = 23;
    private static final int COL_ADJ_DIESEL_PHYSICAL = 24;
    private static final int COL_ADJ_DIESEL_DIFF = 25;

    private static final BigDecimal DEFAULT_GAS_UNIT = new BigDecimal("1600.00");
    private static final BigDecimal DEFAULT_DIESEL_UNIT = new BigDecimal("1450.00");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d")
    );

    private final TransactionMapper transactionMapper;
    private final TankMapper tankMapper;
    private final PriceHistoryMapper priceHistoryMapper;
    private final ExcelUploadLogMapper excelUploadLogMapper;
    private final StockInMapper stockInMapper;
    private final AdjustmentService adjustmentService;
    private final StockEventPublisher stockEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public ExcelUploadResult uploadExcel(MultipartFile file, boolean incrementalOnly, boolean alignOpeningFromExcel) {
        if (file == null || file.isEmpty()) {
            throw new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID, "업로드할 파일이 비어 있습니다.");
        }
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID, ".xlsx 또는 .xls 파일만 업로드할 수 있습니다.");
        }

        Long gasTankId = tankMapper.findByFuelType("GASOLINE").stream()
                .min(Comparator.comparing(Tank::getTankId))
                .map(Tank::getTankId)
                .orElseThrow(() -> new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID,
                        "휘발유(GASOLINE) 탱크가 없습니다."));
        Long dieselTankId = tankMapper.findByFuelType("DIESEL").stream()
                .min(Comparator.comparing(Tank::getTankId))
                .map(Tank::getTankId)
                .orElseThrow(() -> new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID,
                        "경유(DIESEL) 탱크가 없습니다."));

        List<ParsedSheetRow> parsedRows = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID, "워크시트가 없습니다.");
            }
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                LocalDate saleDate = parseDateCell(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                if (saleDate == null) {
                    continue;
                }

                BigDecimal gasIn = parseLiterCell(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal gasSale = parseLiterCell(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal gasDiff = parseSignedAdjLiter(row.getCell(8, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal dieIn = parseLiterCell(row.getCell(9, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal dieSale = parseLiterCell(row.getCell(10, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal dieDiff = parseSignedAdjLiter(row.getCell(15, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal gasSellingPrice = parsePriceCell(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal gasBuyPrice = parsePriceCell(row.getCell(7, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal dieselSellingPrice = parsePriceCell(row.getCell(13, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal dieselBuyPrice = parsePriceCell(row.getCell(14, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal adjGasPhys = parseNonNegativeAdjLiter(row.getCell(COL_ADJ_GAS_PHYSICAL, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal adjGasDiff = parseSignedAdjLiter(row.getCell(COL_ADJ_GAS_DIFF, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal adjDiePhys = parseNonNegativeAdjLiter(row.getCell(COL_ADJ_DIESEL_PHYSICAL, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                BigDecimal adjDieDiff = parseSignedAdjLiter(row.getCell(COL_ADJ_DIESEL_DIFF, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));

                ParsedSheetRow pr = new ParsedSheetRow(r, saleDate, gasIn, gasSale, gasDiff, dieIn, dieSale, dieDiff,
                        gasSellingPrice, gasBuyPrice, dieselSellingPrice, dieselBuyPrice,
                        adjGasPhys, adjGasDiff, adjDiePhys, adjDieDiff);
                if (!pr.hasAnyData()) {
                    continue;
                }
                parsedRows.add(pr);
            }
        } catch (FuelSyncException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Excel parse error", e);
            throw new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID,
                    "엑셀 파일을 읽는 중 오류가 발생했습니다: " + e.getMessage());
        }

        parsedRows.sort(Comparator.comparing(ParsedSheetRow::date).thenComparing(ParsedSheetRow::rowNo));

        if (parsedRows.isEmpty()) {
            throw new FuelSyncException(FuelSyncException.Transaction.EXCEL_INVALID,
                    "저장할 유효한 데이터가 없습니다. 날짜(A열)와 C·D·J·K열(입고·판매 L), G·H·N·O열(가격), 또는 W·X·Y·Z열(재고 조정 L)을 확인해 주세요.");
        }

        String safeName = (name == null || name.isBlank()) ? "upload.xlsx" : name;

        // 업로드 전체가 한 트랜잭션이므로, 시뮬레이션 기준이 되는 잔량 시점부터 행을 잠가 둔다.
        Tank gasTank = tankMapper.findByIdForUpdate(gasTankId)
                .orElseThrow(() -> new FuelSyncException(FuelSyncException.Tank.NOT_FOUND,
                        "탱크를 찾을 수 없습니다. tankId=" + gasTankId));
        Tank dieselTank = tankMapper.findByIdForUpdate(dieselTankId)
                .orElseThrow(() -> new FuelSyncException(FuelSyncException.Tank.NOT_FOUND,
                        "탱크를 찾을 수 없습니다. tankId=" + dieselTankId));

        UploadSimulation simulation = simulateUpload(
                parsedRows, gasTankId, dieselTankId, gasTank, dieselTank, incrementalOnly, alignOpeningFromExcel);

        return persistSimulation(simulation, safeName, incrementalOnly, alignOpeningFromExcel);
    }

    /**
     * Phase 1: DB 쓰기 없이 전체 행을 메모리에서 시뮬레이션하여 잔량·용량 오류를 사전 검증합니다.
     */
    private UploadSimulation simulateUpload(
            List<ParsedSheetRow> parsedRows,
            Long gasTankId,
            Long dieselTankId,
            Tank gasTank,
            Tank dieselTank,
            boolean incrementalOnly,
            boolean alignOpeningFromExcel) {
        Tank simGas = copyTank(gasTank);
        Tank simDiesel = copyTank(dieselTank);
        boolean openingAligned = alignOpeningFromExcel && !incrementalOnly;

        if (openingAligned) {
            simGas = alignOpeningVolumeFromSheet(simGas, parsedRows, true, null);
            simDiesel = alignOpeningVolumeFromSheet(simDiesel, parsedRows, false, null);
        }

        BigDecimal openingGasVolume = simGas.getCurrentVolume();
        BigDecimal openingDieselVolume = simDiesel.getCurrentVolume();

        List<PlannedRow> plannedRows = new ArrayList<>();
        List<Transaction> transactions = new ArrayList<>();
        int skippedDates = 0;
        int adjustmentSkipped = 0;
        boolean gasDailyLevelUpserted = false;
        boolean dieselDailyLevelUpserted = false;

        for (ParsedSheetRow pr : parsedRows) {
            LocalDate d = pr.date();
            int excelRowNo = pr.rowNo() + 1;

            BigDecimal openingGas = simGas.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);
            BigDecimal openingDiesel = simDiesel.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);

            BigDecimal gasUnitTx = pr.hasGasSellingPrice()
                    ? pr.gasSellingPrice()
                    : resolveUnitPrice("GASOLINE", d);
            BigDecimal gasUnitStock = pr.hasGasBuyPrice()
                    ? pr.gasBuyPrice()
                    : resolveUnitPrice("GASOLINE", d);
            BigDecimal dieselUnitTx = pr.hasDieselSellingPrice()
                    ? pr.dieselSellingPrice()
                    : resolveUnitPrice("DIESEL", d);
            BigDecimal dieselUnitStock = pr.hasDieselBuyPrice()
                    ? pr.dieselBuyPrice()
                    : resolveUnitPrice("DIESEL", d);

            boolean gasDataIntent = pr.hasGasIn() || pr.hasGasSale();
            boolean dieselDataIntent = pr.hasDieselIn() || pr.hasDieselSale();
            boolean skipGas = incrementalOnly && gasDataIntent && hasExcelDataForTankDate(gasTankId, d);
            boolean skipDiesel = incrementalOnly && dieselDataIntent && hasExcelDataForTankDate(dieselTankId, d);

            boolean anyInsertFromRow = false;
            StockIn gasStockIn = null;
            StockIn dieselStockIn = null;
            Transaction gasTx = null;
            Transaction dieselTx = null;
            AdjustmentDto.CreateRequest gasAdj = null;
            AdjustmentDto.CreateRequest dieselAdj = null;
            DailyLevelPlan gasDaily = null;
            DailyLevelPlan dieselDaily = null;

            if (!skipGas) {
                if (pr.hasGasIn()) {
                    addVolumeChecked(simGas, pr.gasIn(), d, excelRowNo, "휘발유 입고");
                    gasStockIn = buildStockIn(gasTankId, pr.gasIn(), gasUnitStock, d, null);
                    anyInsertFromRow = true;
                }
                if (pr.hasGasSale()) {
                    deductVolumeChecked(simGas, pr.gasSale(), d, excelRowNo, "휘발유 판매");
                    gasTx = buildTx(gasTankId, pr.gasSale(), gasUnitTx, d);
                    transactions.add(gasTx);
                    anyInsertFromRow = true;
                }
            }
            if (!skipDiesel) {
                if (pr.hasDieselIn()) {
                    addVolumeChecked(simDiesel, pr.dieIn(), d, excelRowNo, "경유 입고");
                    dieselStockIn = buildStockIn(dieselTankId, pr.dieIn(), dieselUnitStock, d, null);
                    anyInsertFromRow = true;
                }
                if (pr.hasDieselSale()) {
                    deductVolumeChecked(simDiesel, pr.dieSale(), d, excelRowNo, "경유 판매");
                    dieselTx = buildTx(dieselTankId, pr.dieSale(), dieselUnitTx, d);
                    transactions.add(dieselTx);
                    anyInsertFromRow = true;
                }
            }

            if (pr.hasGasSellingPrice()) {
                anyInsertFromRow = true;
            }
            if (pr.hasDieselSellingPrice()) {
                anyInsertFromRow = true;
            }

            BigDecimal gasolineClosingLiter = null;
            BigDecimal gasolineDayAdjLiter = null;
            if (!skipGas) {
                BigDecimal closingGas = pr.validGasAdjInsert()
                        ? pr.adjGasPhys().setScale(2, RoundingMode.HALF_UP)
                        : openingGas.add(coalesce(pr.gasIn())).subtract(coalesce(pr.gasSale()))
                                .setScale(2, RoundingMode.HALF_UP);
                gasolineClosingLiter = closingGas;
                gasolineDayAdjLiter = openingGas.subtract(closingGas).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal dieselClosingLiter = null;
            BigDecimal dieselDayAdjLiter = null;
            if (!skipDiesel) {
                BigDecimal closingDiesel = pr.validDieselAdjInsert()
                        ? pr.adjDiePhys().setScale(2, RoundingMode.HALF_UP)
                        : openingDiesel.add(coalesce(pr.dieIn())).subtract(coalesce(pr.dieSale()))
                                .setScale(2, RoundingMode.HALF_UP);
                dieselClosingLiter = closingDiesel;
                dieselDayAdjLiter = openingDiesel.subtract(closingDiesel).setScale(2, RoundingMode.HALF_UP);
            }

            if (!skipGas && pr.validGasAdjInsert()) {
                BigDecimal book = simGas.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);
                BigDecimal physical = pr.adjGasPhys().setScale(2, RoundingMode.HALF_UP);
                if (physical.compareTo(book) != 0) {
                    if (adjustmentService.existsByTankAndDate(gasTankId, d)) {
                        adjustmentSkipped++;
                    } else {
                        gasAdj = new AdjustmentDto.CreateRequest(gasTankId, book, physical, d, null);
                        applyAdjustmentToSimTank(simGas, book, physical, d, excelRowNo, "휘발유 재고조정");
                        anyInsertFromRow = true;
                    }
                }
            }
            if (!skipGas && (pr.hasGasIn() || pr.hasGasSale())) {
                gasDaily = new DailyLevelPlan(
                        gasTankId,
                        d,
                        simGas.getCurrentVolume().setScale(3, RoundingMode.HALF_UP));
                gasDailyLevelUpserted = true;
            }

            if (!skipDiesel && pr.validDieselAdjInsert()) {
                BigDecimal book = simDiesel.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);
                BigDecimal physical = pr.adjDiePhys().setScale(2, RoundingMode.HALF_UP);
                if (physical.compareTo(book) != 0) {
                    if (adjustmentService.existsByTankAndDate(dieselTankId, d)) {
                        adjustmentSkipped++;
                    } else {
                        dieselAdj = new AdjustmentDto.CreateRequest(dieselTankId, book, physical, d, null);
                        applyAdjustmentToSimTank(simDiesel, book, physical, d, excelRowNo, "경유 재고조정");
                        anyInsertFromRow = true;
                    }
                }
            }
            if (!skipDiesel && (pr.hasDieselIn() || pr.hasDieselSale())) {
                dieselDaily = new DailyLevelPlan(
                        dieselTankId,
                        d,
                        simDiesel.getCurrentVolume().setScale(3, RoundingMode.HALF_UP));
                dieselDailyLevelUpserted = true;
            }

            if (pr.hasAnyData() && !anyInsertFromRow) {
                skippedDates++;
            }

            plannedRows.add(new PlannedRow(
                    pr,
                    skipGas,
                    skipDiesel,
                    gasStockIn,
                    dieselStockIn,
                    gasTx,
                    dieselTx,
                    gasAdj,
                    dieselAdj,
                    gasDaily,
                    dieselDaily,
                    gasolineClosingLiter,
                    dieselClosingLiter,
                    gasolineDayAdjLiter,
                    dieselDayAdjLiter));
        }

        if (gasDailyLevelUpserted) {
            simGas = syncCurrentVolumeFromLatestDailyLevel(simGas);
        }
        if (dieselDailyLevelUpserted) {
            simDiesel = syncCurrentVolumeFromLatestDailyLevel(simDiesel);
        }

        return new UploadSimulation(
                parsedRows,
                gasTankId,
                dieselTankId,
                simGas,
                simDiesel,
                plannedRows,
                transactions,
                skippedDates,
                adjustmentSkipped,
                gasDailyLevelUpserted,
                dieselDailyLevelUpserted,
                openingAligned,
                openingGasVolume,
                openingDieselVolume);
    }

    /**
     * Phase 2: 시뮬레이션 성공 후에만 DB에 일괄 저장합니다.
     */
    private ExcelUploadResult persistSimulation(
            UploadSimulation simulation,
            String safeName,
            boolean incrementalOnly,
            boolean alignOpeningFromExcel) {
        ExcelUploadBatch uploadBatch = ExcelUploadBatch.builder()
                .fileName(safeName)
                .totalRows(0)
                .insertedCount(0)
                .build();
        excelUploadLogMapper.insertBatch(uploadBatch);
        Long batchId = uploadBatch.getBatchId();
        String stockMemo = "엑셀 업로드 batchId=" + batchId;

        int overwrittenTransactions = 0;
        int adjustmentExcelDeletes = 0;
        if (!incrementalOnly) {
            Set<String> clearedExcelTxKeys = new HashSet<>();
            Set<String> clearedAdjKeys = new HashSet<>();
            for (ParsedSheetRow pr : simulation.parsedRows()) {
                LocalDate d = pr.date();
                String gasKey = simulation.gasTankId() + "|" + d;
                if (clearedExcelTxKeys.add(gasKey)) {
                    overwrittenTransactions += transactionMapper.deleteExcelByTankAndDate(simulation.gasTankId(), d);
                    stockInMapper.deleteExcelByTankAndDate(simulation.gasTankId(), d);
                    tankMapper.deleteExcelDailyLevelByTankAndDate(simulation.gasTankId(), d.toString());
                }
                String dieselKey = simulation.dieselTankId() + "|" + d;
                if (clearedExcelTxKeys.add(dieselKey)) {
                    overwrittenTransactions += transactionMapper.deleteExcelByTankAndDate(simulation.dieselTankId(), d);
                    stockInMapper.deleteExcelByTankAndDate(simulation.dieselTankId(), d);
                    tankMapper.deleteExcelDailyLevelByTankAndDate(simulation.dieselTankId(), d.toString());
                }
                String gasAdjKey = "adj|" + simulation.gasTankId() + "|" + d;
                if (clearedAdjKeys.add(gasAdjKey)) {
                    adjustmentExcelDeletes += adjustmentService.deleteExcelByTankAndDate(simulation.gasTankId(), d);
                }
                String dieselAdjKey = "adj|" + simulation.dieselTankId() + "|" + d;
                if (clearedAdjKeys.add(dieselAdjKey)) {
                    adjustmentExcelDeletes += adjustmentService.deleteExcelByTankAndDate(simulation.dieselTankId(), d);
                }
            }
        }

        // 잠금 조회는 REPEATABLE READ 스냅샷이 아닌 최신 커밋 값을 읽으므로 여기서도 잠금 조회를 쓴다.
        Tank gasTank = tankMapper.findByIdForUpdate(simulation.gasTankId())
                .orElseThrow(() -> new FuelSyncException(FuelSyncException.Tank.NOT_FOUND,
                        "탱크를 찾을 수 없습니다. tankId=" + simulation.gasTankId()));
        Tank dieselTank = tankMapper.findByIdForUpdate(simulation.dieselTankId())
                .orElseThrow(() -> new FuelSyncException(FuelSyncException.Tank.NOT_FOUND,
                        "탱크를 찾을 수 없습니다. tankId=" + simulation.dieselTankId()));

        if (simulation.openingAligned()) {
            applyOpeningVolume(gasTank, simulation.openingGasVolume());
            applyOpeningVolume(dieselTank, simulation.openingDieselVolume());
            tankMapper.updateVolume(gasTank);
            tankMapper.updateVolume(dieselTank);
        }

        List<ExcelUploadRow> rawRows = new ArrayList<>();
        int stockInInserted = 0;
        int adjustmentInserted = 0;

        for (PlannedRow planned : simulation.plannedRows()) {
            ParsedSheetRow pr = planned.source();

            if (planned.gasStockIn() != null) {
                StockIn stockIn = withMemo(planned.gasStockIn(), stockMemo);
                gasTank.addVolume(stockIn.getInVolume());
                tankMapper.updateVolume(gasTank);
                stockInMapper.insert(stockIn);
                stockInInserted++;
            }
            if (planned.dieselStockIn() != null) {
                StockIn stockIn = withMemo(planned.dieselStockIn(), stockMemo);
                dieselTank.addVolume(stockIn.getInVolume());
                tankMapper.updateVolume(dieselTank);
                stockInMapper.insert(stockIn);
                stockInInserted++;
            }
            if (planned.gasTx() != null) {
                gasTank.deductVolume(planned.gasTx().getLiter());
                tankMapper.updateVolume(gasTank);
            }
            if (planned.dieselTx() != null) {
                dieselTank.deductVolume(planned.dieselTx().getLiter());
                tankMapper.updateVolume(dieselTank);
            }

            if (pr.hasGasSellingPrice()) {
                priceHistoryMapper.upsertFromExcel("GASOLINE", pr.gasSellingPrice(), pr.date());
            }
            if (pr.hasDieselSellingPrice()) {
                priceHistoryMapper.upsertFromExcel("DIESEL", pr.dieselSellingPrice(), pr.date());
            }

            if (planned.gasAdj() != null) {
                AdjustmentDto.CreateRequest adjReq = withMemo(planned.gasAdj(), stockMemo);
                Optional<AdjustmentDto.Response> adjOut = adjustmentService.createExcelIfAbsent(adjReq);
                if (adjOut.isPresent()) {
                    adjustmentInserted++;
                    gasTank = tankMapper.findById(simulation.gasTankId())
                            .orElseThrow(() -> new FuelSyncException(FuelSyncException.Tank.NOT_FOUND,
                                    "탱크를 찾을 수 없습니다. tankId=" + simulation.gasTankId()));
                }
            }
            if (planned.dieselAdj() != null) {
                AdjustmentDto.CreateRequest adjReq = withMemo(planned.dieselAdj(), stockMemo);
                Optional<AdjustmentDto.Response> adjOut = adjustmentService.createExcelIfAbsent(adjReq);
                if (adjOut.isPresent()) {
                    adjustmentInserted++;
                    dieselTank = tankMapper.findById(simulation.dieselTankId())
                            .orElseThrow(() -> new FuelSyncException(FuelSyncException.Tank.NOT_FOUND,
                                    "탱크를 찾을 수 없습니다. tankId=" + simulation.dieselTankId()));
                }
            }

            if (planned.gasDaily() != null) {
                DailyLevelPlan daily = planned.gasDaily();
                tankMapper.upsertDailyLevel(
                        daily.tankId(),
                        daily.levelDate().toString(),
                        daily.endVolumeLiters(),
                        "EXCEL");
            }
            if (planned.dieselDaily() != null) {
                DailyLevelPlan daily = planned.dieselDaily();
                tankMapper.upsertDailyLevel(
                        daily.tankId(),
                        daily.levelDate().toString(),
                        daily.endVolumeLiters(),
                        "EXCEL");
            }

            rawRows.add(ExcelUploadRow.builder()
                    .batchId(batchId)
                    .rowNo(pr.rowNo())
                    .saleDate(pr.date())
                    .gasolineInLiter(pr.gasIn())
                    .gasolineSaleLiter(pr.gasSale())
                    .gasolineDiffLiter(pr.gasDiff())
                    .gasolineSellingPrice(pr.gasSellingPrice())
                    .gasolineBuyPrice(pr.gasBuyPrice())
                    .dieselInLiter(pr.dieIn())
                    .dieselSaleLiter(pr.dieSale())
                    .dieselDiffLiter(pr.dieDiff())
                    .dieselSellingPrice(pr.dieselSellingPrice())
                    .dieselBuyPrice(pr.dieselBuyPrice())
                    .adjGasolinePhysicalLiter(pr.adjGasPhys())
                    .adjGasolineDiffLiter(pr.adjGasDiff())
                    .adjDieselPhysicalLiter(pr.adjDiePhys())
                    .adjDieselDiffLiter(pr.adjDieDiff())
                    .gasolineClosingLiter(planned.gasolineClosingLiter())
                    .dieselClosingLiter(planned.dieselClosingLiter())
                    .gasolineDayAdjLiter(planned.gasolineDayAdjLiter())
                    .dieselDayAdjLiter(planned.dieselDayAdjLiter())
                    .gasStockInInserted(planned.gasStockIn() != null)
                    .gasSaleInserted(planned.gasTx() != null)
                    .dieselStockInInserted(planned.dieselStockIn() != null)
                    .dieselSaleInserted(planned.dieselTx() != null)
                    .build());
        }

        if (simulation.gasDailyLevelUpserted()) {
            gasTank = syncCurrentVolumeFromLatestDailyLevel(gasTank);
        }
        if (simulation.dieselDailyLevelUpserted()) {
            dieselTank = syncCurrentVolumeFromLatestDailyLevel(dieselTank);
        }

        // 업로드가 잔량을 바꾸므로 커밋 후 최종 탱크 상태를 구독자에게 한 번씩 알린다.
        stockEventPublisher.publishTankChange(
                tankMapper.findById(simulation.gasTankId()).orElse(gasTank));
        stockEventPublisher.publishTankChange(
                tankMapper.findById(simulation.dieselTankId()).orElse(dieselTank));

        int chunk = 500;
        for (int i = 0; i < rawRows.size(); i += chunk) {
            int end = Math.min(i + chunk, rawRows.size());
            excelUploadLogMapper.insertRows(new ArrayList<>(rawRows.subList(i, end)));
        }

        List<Transaction> transactions = simulation.transactions();
        for (int i = 0; i < transactions.size(); i += chunk) {
            int end = Math.min(i + chunk, transactions.size());
            transactionMapper.insertBatch(new ArrayList<>(transactions.subList(i, end)));
        }

        int txCount = transactions.size();
        int totalInserted = txCount + stockInInserted;
        int appliedDateCount = countAppliedDates(simulation.plannedRows());
        excelUploadLogMapper.updateBatchCounts(batchId, rawRows.size(), totalInserted, appliedDateCount);
        log.info("[ExcelUpload] batchId={}, file={}, incrementalOnly={}, alignOpeningFromExcel={}, excelRows={}, transactions={}, stockIns={}, overwrittenTx={}, skippedDates={}, adjIns={}, adjSkip={}, adjDel={}",
                batchId, safeName, incrementalOnly, alignOpeningFromExcel, rawRows.size(), txCount, stockInInserted, overwrittenTransactions,
                simulation.skippedDates(), adjustmentInserted, simulation.adjustmentSkipped(), adjustmentExcelDeletes);
        return new ExcelUploadResult(batchId, txCount, stockInInserted, overwrittenTransactions, simulation.skippedDates(),
                adjustmentInserted, simulation.adjustmentSkipped(), adjustmentExcelDeletes);
    }

    private int countAppliedDates(List<PlannedRow> plannedRows) {
        Set<LocalDate> dates = new HashSet<>();
        for (PlannedRow planned : plannedRows) {
            if (planned.gasStockIn() != null || planned.gasTx() != null
                    || planned.dieselStockIn() != null || planned.dieselTx() != null) {
                dates.add(planned.source().date());
            }
        }
        return dates.size();
    }

    private void addVolumeChecked(Tank tank, BigDecimal volume, LocalDate saleDate, int excelRowNo, String action) {
        try {
            tank.addVolume(volume);
        } catch (FuelSyncException e) {
            throw wrapVolumeError(e, saleDate, excelRowNo, action);
        }
    }

    private void deductVolumeChecked(Tank tank, BigDecimal volume, LocalDate saleDate, int excelRowNo, String action) {
        try {
            tank.deductVolume(volume);
        } catch (FuelSyncException e) {
            throw wrapVolumeError(e, saleDate, excelRowNo, action);
        }
    }

    private FuelSyncException wrapVolumeError(
            FuelSyncException e,
            LocalDate saleDate,
            int excelRowNo,
            String action) {
        return new FuelSyncException(e.getErrorCode(),
                e.getMessage() + " (날짜=" + saleDate + ", 엑셀 " + excelRowNo + "행, " + action + ")");
    }

    private void applyAdjustmentToSimTank(
            Tank tank,
            BigDecimal book,
            BigDecimal physical,
            LocalDate saleDate,
            int excelRowNo,
            String action) {
        BigDecimal diff = physical.subtract(book).setScale(2, RoundingMode.HALF_UP);
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            addVolumeChecked(tank, diff, saleDate, excelRowNo, action);
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            deductVolumeChecked(tank, diff.abs(), saleDate, excelRowNo, action);
        }
    }

    private Tank copyTank(Tank source) {
        Tank copy = Tank.builder()
                .tankName(source.getTankName())
                .fuelType(source.getFuelType())
                .maxCapacity(source.getMaxCapacity())
                .currentVolume(source.getCurrentVolume())
                .thresholdWarning(source.getThresholdWarning())
                .thresholdDanger(source.getThresholdDanger())
                .build();
        copy.setTankId(source.getTankId());
        return copy;
    }

    private void applyOpeningVolume(Tank tank, BigDecimal desiredOpening) {
        BigDecimal desired = desiredOpening.setScale(2, RoundingMode.HALF_UP);
        BigDecimal before = tank.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);
        if (before.compareTo(desired) != 0) {
            tank.updateCapacityAndVolume(tank.getMaxCapacity(), desired);
        }
    }

    private StockIn withMemo(StockIn stockIn, String memo) {
        stockIn.setMemo(memo);
        return stockIn;
    }

    private AdjustmentDto.CreateRequest withMemo(AdjustmentDto.CreateRequest request, String memo) {
        return new AdjustmentDto.CreateRequest(
                request.tankId(),
                request.bookStock(),
                request.physicalStock(),
                request.adjDate(),
                memo);
    }

    private Tank alignOpeningVolumeFromSheet(
            Tank tank,
            List<ParsedSheetRow> parsedRows,
            boolean gasoline,
            Long batchId) {
        Optional<OpeningSeed> seedOpt = findOpeningSeed(parsedRows, gasoline);
        if (seedOpt.isEmpty()) {
            return tank;
        }

        OpeningSeed seed = seedOpt.get();
        BigDecimal desiredOpening = seed.openingBook().setScale(2, RoundingMode.HALF_UP);
        if (desiredOpening.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("[ExcelUpload] opening align skipped. batchId={}, tankId={}, fuelType={}, reason=negativeOpening, opening={}, baseDate={}, rowNo={}",
                    batchId, tank.getTankId(), tank.getFuelType(), desiredOpening, seed.baseDate(), seed.rowNo());
            return tank;
        }
        if (desiredOpening.compareTo(tank.getMaxCapacity()) > 0) {
            log.warn("[ExcelUpload] opening align skipped. batchId={}, tankId={}, fuelType={}, reason=overflow, opening={}, maxCapacity={}, baseDate={}, rowNo={}",
                    batchId, tank.getTankId(), tank.getFuelType(), desiredOpening, tank.getMaxCapacity(), seed.baseDate(), seed.rowNo());
            return tank;
        }

        BigDecimal before = tank.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);
        if (before.compareTo(desiredOpening) == 0) {
            return tank;
        }

        tank.updateCapacityAndVolume(tank.getMaxCapacity(), desiredOpening);
        tankMapper.updateVolume(tank);
        log.info("[ExcelUpload] opening aligned. batchId={}, tankId={}, fuelType={}, before={}, after={}, baseDate={}, rowNo={}, physicalClose={}, inVolume={}, saleVolume={}",
                batchId, tank.getTankId(), tank.getFuelType(), before, desiredOpening, seed.baseDate(), seed.rowNo(),
                seed.physicalClose(), seed.inVolume(), seed.saleVolume());
        return tank;
    }

    private Optional<OpeningSeed> findOpeningSeed(List<ParsedSheetRow> parsedRows, boolean gasoline) {
        return parsedRows.stream()
                .filter(row -> gasoline ? row.validGasAdjInsert() : row.validDieselAdjInsert())
                .map(row -> {
                    BigDecimal physical = gasoline ? row.adjGasPhys() : row.adjDiePhys();
                    BigDecimal inVolume = coalesce(gasoline ? row.gasIn() : row.dieIn());
                    BigDecimal saleVolume = coalesce(gasoline ? row.gasSale() : row.dieSale());
                    BigDecimal openingBook = physical.add(saleVolume).subtract(inVolume);
                    return new OpeningSeed(row.rowNo(), row.date(), physical, inVolume, saleVolume, openingBook);
                })
                .min(Comparator.comparing(OpeningSeed::baseDate).thenComparing(OpeningSeed::rowNo));
    }

    private BigDecimal coalesce(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private boolean hasExcelDataForTankDate(long tankId, LocalDate d) {
        return transactionMapper.countExcelByTankAndDate(tankId, d) > 0
                || stockInMapper.countExcelByTankAndDate(tankId, d) > 0;
    }

    private Tank syncCurrentVolumeFromLatestDailyLevel(Tank tank) {
        BigDecimal latestDailyLevel = tankMapper.findLatestDailyLevelEndVolume(tank.getTankId());
        if (latestDailyLevel == null) {
            return tank;
        }

        BigDecimal targetVolume = latestDailyLevel.setScale(2, RoundingMode.HALF_UP);
        if (tank.getCurrentVolume().setScale(2, RoundingMode.HALF_UP).compareTo(targetVolume) == 0) {
            return tank;
        }

        tank.updateCapacityAndVolume(tank.getMaxCapacity(), targetVolume);
        tankMapper.updateCapacityAndVolume(tank);
        log.info("[ExcelUpload] tank current volume synced from daily level. tankId={}, fuelType={}, volume={}",
                tank.getTankId(), tank.getFuelType(), targetVolume);
        return tank;
    }

    private StockIn buildStockIn(Long tankId, BigDecimal inVolume, BigDecimal buyPrice, LocalDate inDate, String memo) {
        return StockIn.builder()
                .tankId(tankId)
                .inVolume(inVolume.setScale(2, RoundingMode.HALF_UP))
                .buyPrice(buyPrice.setScale(2, RoundingMode.HALF_UP))
                .vendorName(null)
                .inDate(inDate)
                .memo(memo)
                .inputType("EXCEL")
                .build();
    }

    private record ParsedSheetRow(
            int rowNo,
            LocalDate date,
            BigDecimal gasIn,
            BigDecimal gasSale,
            BigDecimal gasDiff,
            BigDecimal dieIn,
            BigDecimal dieSale,
            BigDecimal dieDiff,
            BigDecimal gasSellingPrice,
            BigDecimal gasBuyPrice,
            BigDecimal dieselSellingPrice,
            BigDecimal dieselBuyPrice,
            BigDecimal adjGasPhys,
            BigDecimal adjGasDiff,
            BigDecimal adjDiePhys,
            BigDecimal adjDieDiff
    ) {
        boolean hasAnyData() {
            return hasGasIn() || hasGasSale() || hasDieselIn() || hasDieselSale()
                    || hasGasSellingPrice() || hasGasBuyPrice() || hasDieselSellingPrice() || hasDieselBuyPrice()
                    || validGasAdjInsert() || validDieselAdjInsert();
        }

        boolean hasGasIn() {
            return gasIn != null && gasIn.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasGasSale() {
            return gasSale != null && gasSale.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasDieselIn() {
            return dieIn != null && dieIn.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasDieselSale() {
            return dieSale != null && dieSale.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasGasSellingPrice() {
            return gasSellingPrice != null && gasSellingPrice.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasGasBuyPrice() {
            return gasBuyPrice != null && gasBuyPrice.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasDieselSellingPrice() {
            return dieselSellingPrice != null && dieselSellingPrice.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasDieselBuyPrice() {
            return dieselBuyPrice != null && dieselBuyPrice.compareTo(BigDecimal.ZERO) > 0;
        }

        // 마감 잔여량(W/Y) 한 컬럼만으로 조정 INSERT 가능 여부를 결정한다.
        // X/Z는 사용자가 적은 차이값일 뿐이며 계산에 사용하지 않는다.
        boolean validGasAdjInsert() {
            return adjGasPhys != null && adjGasPhys.compareTo(BigDecimal.ZERO) >= 0;
        }

        boolean validDieselAdjInsert() {
            return adjDiePhys != null && adjDiePhys.compareTo(BigDecimal.ZERO) >= 0;
        }
    }

    private record DailyLevelPlan(Long tankId, LocalDate levelDate, BigDecimal endVolumeLiters) {}

    private record PlannedRow(
            ParsedSheetRow source,
            boolean skipGas,
            boolean skipDiesel,
            StockIn gasStockIn,
            StockIn dieselStockIn,
            Transaction gasTx,
            Transaction dieselTx,
            AdjustmentDto.CreateRequest gasAdj,
            AdjustmentDto.CreateRequest dieselAdj,
            DailyLevelPlan gasDaily,
            DailyLevelPlan dieselDaily,
            BigDecimal gasolineClosingLiter,
            BigDecimal dieselClosingLiter,
            BigDecimal gasolineDayAdjLiter,
            BigDecimal dieselDayAdjLiter
    ) {}

    private record UploadSimulation(
            List<ParsedSheetRow> parsedRows,
            Long gasTankId,
            Long dieselTankId,
            Tank finalGasTank,
            Tank finalDieselTank,
            List<PlannedRow> plannedRows,
            List<Transaction> transactions,
            int skippedDates,
            int adjustmentSkipped,
            boolean gasDailyLevelUpserted,
            boolean dieselDailyLevelUpserted,
            boolean openingAligned,
            BigDecimal openingGasVolume,
            BigDecimal openingDieselVolume
    ) {}

    private record OpeningSeed(
            int rowNo,
            LocalDate baseDate,
            BigDecimal physicalClose,
            BigDecimal inVolume,
            BigDecimal saleVolume,
            BigDecimal openingBook
    ) {}

    private Transaction buildTx(Long tankId, BigDecimal liter, BigDecimal unitPrice, LocalDate saleDate) {
        BigDecimal total = liter.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        return Transaction.builder()
                .tankId(tankId)
                .liter(liter.setScale(3, RoundingMode.HALF_UP))
                .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                .totalAmount(total)
                .paymentType(null)
                .inputType(Transaction.InputType.EXCEL)
                .approvalNo(null)
                .txDatetime(LocalDateTime.of(saleDate, LocalTime.MIDNIGHT))
                .build();
    }

    private BigDecimal resolveUnitPrice(String fuelType, LocalDate saleDate) {
        BigDecimal fromHistory = priceHistoryMapper.findLatestOurPriceByFuelTypeOnOrBefore(fuelType, saleDate);
        if (fromHistory != null && fromHistory.compareTo(BigDecimal.ZERO) > 0) {
            return fromHistory;
        }
        return "GASOLINE".equals(fuelType) ? DEFAULT_GAS_UNIT : DEFAULT_DIESEL_UNIT;
    }

    private LocalDate parseDateCell(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double num = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                if (DateUtil.isValidExcelDate(num)) {
                    return DateUtil.getJavaDate(num).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                }
            }
            if (cell.getCellType() == CellType.FORMULA) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                CellType cached = cell.getCachedFormulaResultType();
                if (cached == CellType.NUMERIC) {
                    double num = cell.getNumericCellValue();
                    if (DateUtil.isValidExcelDate(num)) {
                        return DateUtil.getJavaDate(num).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    }
                }
                if (cached == CellType.STRING) {
                    return parseDateString(cell.getStringCellValue());
                }
            }
        } catch (Exception ignored) {
            // fall through to string parse
        }
        return parseDateString(getCellAsString(cell));
    }

    private LocalDate parseDateString(String raw) {
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (raw.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private String getCellAsString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield DATA_FORMATTER.formatCellValue(cell);
            }
            case FORMULA -> {
                try {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    }
                    yield switch (cell.getCachedFormulaResultType()) {
                        case STRING -> cell.getStringCellValue();
                        case NUMERIC -> DATA_FORMATTER.formatCellValue(cell);
                        case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                        default -> "";
                    };
                } catch (Exception e) {
                    yield "";
                }
            }
            default -> "";
        };
    }

    /** C·D·J·K 등 리터 열: 0 이상, 음수는 무시 */
    private BigDecimal parseLiterCell(Cell cell) {
        BigDecimal v = parseNumericCell(cell);
        return v == null || v.compareTo(BigDecimal.ZERO) < 0 ? null : v;
    }

    /** G·H·N·O 열: 리터당 가격(원), 0 이하는 무시 */
    private BigDecimal parsePriceCell(Cell cell) {
        BigDecimal v = parseNumericCell(cell);
        return v == null || v.compareTo(BigDecimal.ZERO) <= 0 ? null : v;
    }

    /** W·Y: 실측 재고(L), 0 이상 */
    private BigDecimal parseNonNegativeAdjLiter(Cell cell) {
        BigDecimal v = parseSignedAdjLiter(cell);
        if (v == null) {
            return null;
        }
        return v.compareTo(BigDecimal.ZERO) < 0 ? null : v;
    }

    /** X·Z: 재고 조정값(L), 음수·0·양수 */
    private BigDecimal parseSignedAdjLiter(Cell cell) {
        return parseNumericCell(cell);
    }

    /**
     * 숫자 셀(숫자·쉼표 포함 문자열·수식 캐시값)을 BigDecimal로 읽는다.
     * 빈 셀, "-", 숫자가 아닌 값, NaN/무한대는 null. 부호 검증은 호출하는 쪽에서 한다.
     */
    private BigDecimal parseNumericCell(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            double v;
            switch (cell.getCellType()) {
                case NUMERIC -> v = cell.getNumericCellValue();
                case STRING -> {
                    Double parsed = parseNumericText(cell.getStringCellValue());
                    if (parsed == null) {
                        return null;
                    }
                    v = parsed;
                }
                case FORMULA -> {
                    CellType cached = cell.getCachedFormulaResultType();
                    if (cached == CellType.NUMERIC) {
                        v = cell.getNumericCellValue();
                    } else if (cached == CellType.STRING) {
                        Double parsed = parseNumericText(cell.getStringCellValue());
                        if (parsed == null) {
                            return null;
                        }
                        v = parsed;
                    } else {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            }
            return BigDecimal.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** 쉼표를 제거하고 숫자로 변환. 빈 문자열·"-"는 null, 숫자가 아니면 NumberFormatException. */
    private Double parseNumericText(String raw) {
        String s = raw.replace(",", "").trim();
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }
        return Double.parseDouble(s);
    }
}
