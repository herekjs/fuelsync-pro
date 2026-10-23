package com.fuelsync.domain.excel.mapper;

import com.fuelsync.domain.excel.model.ExcelUploadBatch;
import com.fuelsync.domain.excel.model.ExcelUploadRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExcelUploadLogMapper {

    void insertBatch(ExcelUploadBatch batch);

    void insertRows(@Param("rows") List<ExcelUploadRow> rows);

    void updateBatchCounts(
            @Param("batchId") Long batchId,
            @Param("totalRows") int totalRows,
            @Param("insertedCount") int insertedCount,
            @Param("appliedDateCount") int appliedDateCount
    );

    List<ExcelUploadBatch> findAllBatchesDesc();

    List<ExcelUploadBatch> findBatchesDescByUploadedAtRange(
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);

    List<ExcelUploadRow> findRowsByBatchId(@Param("batchId") Long batchId);

    /** 날짜별 판매차(엑셀 I·P열); 동일 날짜 다중 행 시 row_id 최신 1건만 */
    List<Map<String, Object>> findDailyDiffStats(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
}
