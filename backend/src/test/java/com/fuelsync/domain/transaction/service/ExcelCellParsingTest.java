package com.fuelsync.domain.transaction.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 엑셀 숫자 셀 파서 3종(리터·가격·조정량)의 현재 동작을 고정한다. */
class ExcelCellParsingTest {

    private XSSFWorkbook wb;
    private Row row;
    private FormulaEvaluator evaluator;
    private ExcelUploadService service;
    private int col;

    @BeforeEach
    void setUp() {
        wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        row = sheet.createRow(0);
        evaluator = wb.getCreationHelper().createFormulaEvaluator();
        service = new ExcelUploadService(null, null, null, null, null, null);
        col = 0;
    }

    @AfterEach
    void tearDown() throws Exception {
        wb.close();
    }

    private Cell numeric(double v) {
        Cell c = row.createCell(col++);
        c.setCellValue(v);
        return c;
    }

    private Cell text(String s) {
        Cell c = row.createCell(col++);
        c.setCellValue(s);
        return c;
    }

    private Cell formula(String f) {
        Cell c = row.createCell(col++);
        c.setCellFormula(f);
        evaluator.evaluateFormulaCell(c);
        return c;
    }

    private BigDecimal parse(String method, Cell cell) {
        return ReflectionTestUtils.invokeMethod(service, method, cell);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        if (expected == null) {
            assertNull(actual);
        } else {
            assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but " + actual);
        }
    }

    // ----- 공통: 숫자·문자·수식·빈 셀 처리 -----

    @Test
    void allParsers_readNumericStringAndFormulaCells() {
        for (String m : new String[]{"parseLiterCell", "parsePriceCell", "parseSignedAdjLiter"}) {
            col = 0;
            row = wb.getSheetAt(0).createRow(1);
            assertDecimal("1234.5", parse(m, numeric(1234.5)));
            assertDecimal("1234", parse(m, text("1,234")));
            assertDecimal("7", parse(m, text(" 7 ")));
            assertDecimal("5", parse(m, formula("2+3")));
            assertDecimal("1234", parse(m, formula("\"1,234\"")));
        }
    }

    @Test
    void allParsers_returnNullForBlankDashInvalidAndBoolean() {
        for (String m : new String[]{"parseLiterCell", "parsePriceCell", "parseSignedAdjLiter"}) {
            col = 0;
            row = wb.getSheetAt(0).createRow(2);
            assertNull(parse(m, null));
            assertNull(parse(m, row.createCell(col++)));
            assertNull(parse(m, text("")));
            assertNull(parse(m, text("-")));
            assertNull(parse(m, text("abc")));
            assertNull(parse(m, text("1,234L")));
            Cell bool = row.createCell(col++);
            bool.setCellValue(true);
            assertNull(parse(m, bool));
        }
    }

    // ----- 파서별 부호 규칙 -----

    @Test
    void liter_rejectsNegativeButKeepsZero() {
        assertNull(parse("parseLiterCell", numeric(-1)));
        assertDecimal("0", parse("parseLiterCell", numeric(0)));
    }

    @Test
    void price_rejectsZeroAndNegative() {
        assertNull(parse("parsePriceCell", numeric(0)));
        assertNull(parse("parsePriceCell", numeric(-1)));
        assertDecimal("1999", parse("parsePriceCell", numeric(1999)));
    }

    @Test
    void signedAdj_keepsNegativeAndZero() {
        assertDecimal("-5", parse("parseSignedAdjLiter", numeric(-5)));
        assertDecimal("0", parse("parseSignedAdjLiter", numeric(0)));
    }

    @Test
    void nonNegativeAdj_rejectsNegativeButKeepsZero() {
        assertNull(parse("parseNonNegativeAdjLiter", numeric(-1)));
        assertDecimal("0", parse("parseNonNegativeAdjLiter", numeric(0)));
        assertDecimal("88453.5", parse("parseNonNegativeAdjLiter", numeric(88453.5)));
    }
}
