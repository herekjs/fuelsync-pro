import { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { excelUploadApi } from '../services/api';
import type { ExcelUploadBatch, ExcelUploadRow } from '../types';
import { toast } from 'sonner';

/** 이력삭제 버튼 표시 */
const SHOW_PURGE_BUTTON = true;

function fmtL(v: number | null | undefined): string {
  if (v == null) return '—';
  return Number(v).toLocaleString();
}

function fmtWon(v: number | null | undefined): string {
  if (v == null) return '—';
  return `₩${Math.round(Number(v)).toLocaleString()}`;
}

const APPLIED_CELL_CLASS =
  'bg-emerald-50 text-emerald-900 ring-1 ring-inset ring-emerald-200 font-medium';

function appliedCellClass(inserted?: boolean): string {
  return inserted ? `px-4 py-3 ${APPLIED_CELL_CLASS}` : 'px-4 py-3';
}

function countInsertedCells(rows: ExcelUploadRow[]): number {
  return rows.reduce(
    (sum, r) =>
      sum +
      (r.gasStockInInserted ? 1 : 0) +
      (r.gasSaleInserted ? 1 : 0) +
      (r.dieselStockInInserted ? 1 : 0) +
      (r.dieselSaleInserted ? 1 : 0),
    0,
  );
}

function rowsHaveInsertFlags(rows: ExcelUploadRow[]): boolean {
  return rows.some(
    (r) =>
      r.gasStockInInserted !== undefined ||
      r.gasSaleInserted !== undefined ||
      r.dieselStockInInserted !== undefined ||
      r.dieselSaleInserted !== undefined,
  );
}

export default function ExcelUploadLogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [fromDate, setFromDate] = useState(() => dayjs().subtract(1, 'month').format('YYYY-MM-DD'));
  const [toDate, setToDate] = useState(() => dayjs().format('YYYY-MM-DD'));
  /** 조회 기간이 실제로 바뀐 경우에만 batchId 제거 (setSearchParams 참조 변화로 오동작 방지) */
  const prevDateRangeRef = useRef<{ from: string; to: string } | null>(null);
  const hasLoadedBatchesOnce = useRef(false);

  const [batches, setBatches] = useState<ExcelUploadBatch[]>([]);
  const [rows, setRows] = useState<ExcelUploadRow[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [rowsLoading, setRowsLoading] = useState(false);
  const [purgeLoading, setPurgeLoading] = useState(false);

  const loadBatches = useCallback(async () => {
    setLoading(true);
    try {
      const res = await excelUploadApi.getBatches(fromDate, toDate);
      setBatches(res.data.data);
    } catch {
      toast.error('업로드 이력을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
      hasLoadedBatchesOnce.current = true;
    }
  }, [fromDate, toDate]);

  useEffect(() => {
    void loadBatches();
  }, [loadBatches]);

  useEffect(() => {
    if (prevDateRangeRef.current === null) {
      prevDateRangeRef.current = { from: fromDate, to: toDate };
      return;
    }
    if (prevDateRangeRef.current.from === fromDate && prevDateRangeRef.current.to === toDate) {
      return;
    }
    prevDateRangeRef.current = { from: fromDate, to: toDate };
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('batchId');
      return next;
    });
  }, [fromDate, toDate, setSearchParams]);

  const loadRows = useCallback(async (batchId: number) => {
    setRowsLoading(true);
    setSelectedId(batchId);
    try {
      const res = await excelUploadApi.getRows(batchId);
      setRows(res.data.data);
    } catch {
      toast.error('원본 행을 불러오지 못했습니다.');
      setRows([]);
    } finally {
      setRowsLoading(false);
    }
  }, []);

  const closeRowsModal = useCallback(() => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('batchId');
      return next;
    });
  }, [setSearchParams]);

  useEffect(() => {
    const q = searchParams.get('batchId');
    if (!q) {
      setSelectedId(null);
      setRows([]);
      return;
    }
    const id = Number(q);
    if (!Number.isFinite(id) || id <= 0) {
      setSelectedId(null);
      setRows([]);
      return;
    }
    void loadRows(id);
  }, [searchParams, loadRows]);

  useEffect(() => {
    if (selectedId == null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeRowsModal();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selectedId, closeRowsModal]);

  useEffect(() => {
    if (selectedId == null) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [selectedId]);

  const selectBatch = (b: ExcelUploadBatch) => {
    setSearchParams({ batchId: String(b.batchId) });
  };

  const handleSearch = () => {
    void loadBatches();
  };

  const handlePurgeOperationalData = () => {
    if (
      !window.confirm(
        '엑셀 업로드 행·배치, 판매(tb_transaction), 입고(tb_stock_in), 재고조정(tb_stock_adjustment), 유가이력(tb_price_history)을 모두 삭제하고 AUTO_INCREMENT를 1로 초기화합니다. 탱크·판매가·사용자 등은 그대로입니다. 계속할까요?',
      )
    ) {
      return;
    }
    setPurgeLoading(true);
    void excelUploadApi
      .purgeOperationalData()
      .then(() => {
        toast.success('운영 이력 데이터가 삭제되었습니다.');
        void loadBatches();
        setSelectedId(null);
        setRows([]);
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev);
          next.delete('batchId');
          return next;
        });
      })
      .catch((err: { response?: { data?: { message?: string } } }) => {
        const msg = err.response?.data?.message ?? '삭제에 실패했습니다.';
        toast.error(msg);
      })
      .finally(() => setPurgeLoading(false));
  };

  const selectedBatch = selectedId != null ? batches.find((b) => b.batchId === selectedId) : undefined;
  const selectedFileName = selectedBatch?.fileName;
  const insertedCellCount = useMemo(() => countInsertedCells(rows), [rows]);
  const showInsertFlags = rowsHaveInsertFlags(rows);

  if (loading && !hasLoadedBatchesOnce.current) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">엑셀 업로드 이력</h1>
        <p className="text-sm text-gray-600 mt-2">
          <strong>저장 건수</strong>는 한 업로드에서 생성된 <code className="bg-gray-100 px-1 rounded">tb_transaction</code>(D·K열 판매) 건수와{' '}
          <code className="bg-gray-100 px-1 rounded">tb_stock_in</code>(C·J열 입고) 건수를 합친 값입니다.{' '}
          <strong>반영 일수</strong>는 그중 판매·입고가 실제로 INSERT된 <strong className="text-gray-800">서로 다른 날짜 수</strong>입니다. 한 엑셀 행에 입고·판매가
          여러 번 나오면 저장 건수가 행 수보다 클 수 있습니다. 원본 보기에는 시트 순서 외에 <strong className="text-gray-800">날짜·행번호 기준 정렬</strong>로
          저장되며, W·Y가 비어 있으면 그 행 시작 잔량+입고−판매로 계산 마감·조정량(행시작−마감)이 채워집니다.
        </p>
      </div>

      <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
        <div className="flex flex-wrap items-end gap-3">
          <Field label="조회 시작일">
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="input"
            />
          </Field>
          <Field label="조회 종료일">
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="input"
            />
          </Field>
          <button
            type="button"
            onClick={handleSearch}
            className="px-5 py-2 bg-gray-700 text-white rounded-xl text-sm font-medium hover:bg-gray-800 transition-colors"
          >
            조회
          </button>
          {SHOW_PURGE_BUTTON && (
            <button
              type="button"
              onClick={handlePurgeOperationalData}
              disabled={purgeLoading}
              className="px-5 py-2 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-60 transition-colors"
            >
              {purgeLoading ? '삭제 중…' : '이력삭제'}
            </button>
          )}
        </div>
      </div>

      <section className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 bg-gray-50 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold text-gray-800">업로드 목록</h2>
          {loading && hasLoadedBatchesOnce.current && (
            <span className="text-xs text-gray-500">불러오는 중…</span>
          )}
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-100">
                <th className="px-4 py-3 font-medium">업로드 시각</th>
                <th className="px-4 py-3 font-medium">파일명</th>
                <th className="px-4 py-3 font-medium">엑셀 유효 행</th>
                <th className="px-4 py-3 font-medium">저장 건수</th>
                <th className="px-4 py-3 font-medium">반영 일수</th>
                <th className="px-4 py-3 font-medium" />
              </tr>
            </thead>
            <tbody>
              {batches.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-gray-500">
                    해당 기간에 업로드 이력이 없습니다.
                  </td>
                </tr>
              ) : (
                batches.map((b) => (
                  <tr
                    key={b.batchId}
                    className={`border-b border-gray-50 hover:bg-blue-50/50 cursor-pointer ${
                      selectedId === b.batchId ? 'bg-blue-50' : ''
                    }`}
                    onClick={() => selectBatch(b)}
                  >
                    <td className="px-4 py-3 text-gray-800">{dayjs(b.uploadedAt).format('YYYY-MM-DD HH:mm:ss')}</td>
                    <td className="px-4 py-3 font-medium text-gray-900">{b.fileName}</td>
                    <td className="px-4 py-3">{b.totalRows.toLocaleString()}</td>
                    <td className="px-4 py-3 tabular-nums">{b.insertedCount.toLocaleString()}</td>
                    <td className="px-4 py-3 tabular-nums">{(b.appliedDateCount ?? 0).toLocaleString()}</td>
                    <td className="px-4 py-3 text-blue-600 text-xs font-medium">원본 보기 →</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selectedId != null && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/50"
          onClick={closeRowsModal}
          role="presentation"
        >
          <div
            className="bg-white rounded-2xl shadow-xl border border-gray-200 w-full max-w-[min(1400px,96vw)] max-h-[90vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="excel-original-rows-title"
          >
            <div className="px-4 py-3 border-b border-gray-100 bg-gray-50 flex items-start gap-3 shrink-0 rounded-t-2xl">
              <div className="flex-1 min-w-0">
                <h2 id="excel-original-rows-title" className="text-sm font-semibold text-gray-900">
                  원본 엑셀 행
                </h2>
                <p className="text-xs text-gray-600 mt-0.5 truncate" title={selectedFileName}>
                  배치 #{selectedId}
                  {selectedFileName ? ` · ${selectedFileName}` : ''}
                </p>
                {showInsertFlags ? (
                  <p className="text-xs text-gray-600 mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="inline-flex items-center gap-1.5">
                      <span className={`inline-block w-3 h-3 rounded-sm ${APPLIED_CELL_CLASS}`} aria-hidden />
                      DB에 저장된 입고·판매 (C·D·J·K)
                    </span>
                    <span>
                      저장 <strong className="text-emerald-800">{insertedCellCount.toLocaleString()}건</strong>
                      {selectedBatch != null
                        ? ` · 목록 저장 ${selectedBatch.insertedCount.toLocaleString()}건 · 반영 ${(selectedBatch.appliedDateCount ?? 0).toLocaleString()}일`
                        : ''}
                      {rows.length > 0 ? ` · 전체 ${rows.length.toLocaleString()}행` : ''}
                    </span>
                  </p>
                ) : (
                  !rowsLoading &&
                  rows.length > 0 && (
                    <p className="text-xs text-amber-700 mt-1.5">
                      이 배치는 적용 정보가 없습니다. 컬럼 추가 이전 업로드이거나 DB 마이그레이션이 필요합니다.
                    </p>
                  )
                )}
              </div>
              {rowsLoading && <span className="text-xs text-gray-500 shrink-0">불러오는 중…</span>}
              <button
                type="button"
                onClick={closeRowsModal}
                className="shrink-0 rounded-lg p-1.5 text-gray-500 hover:bg-gray-200/80 hover:text-gray-800 transition-colors"
                aria-label="닫기"
              >
                <span className="text-lg leading-none font-light" aria-hidden>
                  ×
                </span>
              </button>
            </div>
            <div className="overflow-auto flex-1 min-h-0 rounded-b-2xl">
              <table className="min-w-full text-sm">
                <thead className="sticky top-0 bg-gray-50 z-10 shadow-sm">
                  <tr className="text-left text-gray-500 border-b border-gray-100">
                    <th className="px-4 py-3 font-medium">시트 행 (1부터)</th>
                    <th className="px-4 py-3 font-medium">날짜 (A열)</th>
                    <th className="px-4 py-3 font-medium">휘발유 입고 C (L)</th>
                    <th className="px-4 py-3 font-medium">휘발유 판매 D (L)</th>
                    <th className="px-4 py-3 font-medium">휘발유 판매차 I (L)</th>
                    <th className="px-4 py-3 font-medium">경유 입고 J (L)</th>
                    <th className="px-4 py-3 font-medium">경유 판매 K (L)</th>
                    <th className="px-4 py-3 font-medium">경유 판매차 P (L)</th>
                    <th className="px-4 py-3 font-medium">휘발유 판매가 G</th>
                    <th className="px-4 py-3 font-medium">휘발유 재고가 H</th>
                    <th className="px-4 py-3 font-medium">경유 판매가 N</th>
                    <th className="px-4 py-3 font-medium">경유 재고가 O</th>
                    <th className="px-4 py-3 font-medium">휘발유 실측 W (L)</th>
                    <th className="px-4 py-3 font-medium">휘발유 조정 X (L)</th>
                    <th className="px-4 py-3 font-medium">휘발유 계산 마감 (L)</th>
                    <th className="px-4 py-3 font-medium">휘발유 행 조정량 (L)</th>
                    <th className="px-4 py-3 font-medium">경유 실측 Y (L)</th>
                    <th className="px-4 py-3 font-medium">경유 조정 Z (L)</th>
                    <th className="px-4 py-3 font-medium">경유 계산 마감 (L)</th>
                    <th className="px-4 py-3 font-medium">경유 행 조정량 (L)</th>
                  </tr>
                </thead>
                <tbody>
                  {!rowsLoading &&
                    rows.map((r) => (
                      <tr key={r.rowId} className="border-b border-gray-50">
                        <td className="px-4 py-3 font-mono text-gray-700">{r.rowNo + 1}</td>
                        <td className="px-4 py-3">{r.saleDate}</td>
                        <td className={appliedCellClass(r.gasStockInInserted)}>{fmtL(r.gasolineInLiter)}</td>
                        <td className={appliedCellClass(r.gasSaleInserted)}>{fmtL(r.gasolineSaleLiter)}</td>
                        <td className="px-4 py-3">{fmtL(r.gasolineDiffLiter)}</td>
                        <td className={appliedCellClass(r.dieselStockInInserted)}>{fmtL(r.dieselInLiter)}</td>
                        <td className={appliedCellClass(r.dieselSaleInserted)}>{fmtL(r.dieselSaleLiter)}</td>
                        <td className="px-4 py-3">{fmtL(r.dieselDiffLiter)}</td>
                        <td className="px-4 py-3">{fmtWon(r.gasolineSellingPrice)}</td>
                        <td className="px-4 py-3">{fmtWon(r.gasolineBuyPrice)}</td>
                        <td className="px-4 py-3">{fmtWon(r.dieselSellingPrice)}</td>
                        <td className="px-4 py-3">{fmtWon(r.dieselBuyPrice)}</td>
                        <td className="px-4 py-3">{fmtL(r.adjGasolinePhysicalLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.adjGasolineDiffLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.gasolineClosingLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.gasolineDayAdjLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.adjDieselPhysicalLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.adjDieselDiffLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.dieselClosingLiter ?? null)}</td>
                        <td className="px-4 py-3">{fmtL(r.dieselDayAdjLiter ?? null)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
              {!rowsLoading && rows.length === 0 && (
                <p className="px-6 py-8 text-center text-gray-500 text-sm">원본 행이 없습니다.</p>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      {children}
    </div>
  );
}
