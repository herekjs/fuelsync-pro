package com.fuelsync.domain.maintenance.service;



import com.fuelsync.domain.maintenance.mapper.OperationalDataPurgeMapper;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



@Service

@RequiredArgsConstructor

@Slf4j

public class OperationalDataPurgeService {



    private final OperationalDataPurgeMapper operationalDataPurgeMapper;



    @Transactional

    public void purgeUploadAndOperationalTables(String username) {

        operationalDataPurgeMapper.deleteAllExcelUploadRows();

        operationalDataPurgeMapper.deleteAllExcelUploadBatches();

        operationalDataPurgeMapper.deleteAllTankDailyLevels();

        operationalDataPurgeMapper.deleteAllTransactions();

        operationalDataPurgeMapper.deleteAllStockIns();

        operationalDataPurgeMapper.deleteAllStockAdjustments();

        operationalDataPurgeMapper.deleteAllPriceHistory();

        operationalDataPurgeMapper.resetExcelUploadRowAutoIncrement();

        operationalDataPurgeMapper.resetExcelUploadBatchAutoIncrement();

        operationalDataPurgeMapper.resetTransactionAutoIncrement();

        operationalDataPurgeMapper.resetStockInAutoIncrement();

        operationalDataPurgeMapper.resetStockAdjustmentAutoIncrement();

        operationalDataPurgeMapper.resetPriceHistoryAutoIncrement();

        log.warn("[OperationalDataPurge] All upload/operational tables truncated by user={}", username);

    }

}

