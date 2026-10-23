package com.fuelsync.domain.excel.service;

import com.fuelsync.domain.excel.dto.ExcelUploadLogDto;
import com.fuelsync.domain.excel.mapper.ExcelUploadLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExcelUploadLogService {

    private final ExcelUploadLogMapper excelUploadLogMapper;

    public List<ExcelUploadLogDto.BatchResponse> findBatches(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return excelUploadLogMapper.findAllBatchesDesc().stream()
                    .map(ExcelUploadLogDto.BatchResponse::from)
                    .toList();
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        return excelUploadLogMapper.findBatchesDescByUploadedAtRange(start, toExclusive).stream()
                .map(ExcelUploadLogDto.BatchResponse::from)
                .toList();
    }

    public List<ExcelUploadLogDto.RowResponse> findRowsByBatchId(Long batchId) {
        return excelUploadLogMapper.findRowsByBatchId(batchId).stream()
                .map(ExcelUploadLogDto.RowResponse::from)
                .toList();
    }
}
