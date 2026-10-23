import { useCallback, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { transactionApi } from '../../services/api';
import type { AxiosError } from 'axios';
import type { CommonResponse } from '../../types';

type UploadState = 'idle' | 'uploading' | 'success' | 'error';

function getErrorMessage(err: unknown): string {
  const ax = err as AxiosError<CommonResponse<unknown>>;
  const msg = ax.response?.data?.message;
  if (msg) return msg;
  if (ax.message) return ax.message;
  return '업로드 중 오류가 발생했습니다.';
}

export default function ExcelUploadWidget({ onUploadSuccess }: { onUploadSuccess?: () => void | Promise<void> }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [state, setState] = useState<UploadState>('idle');
  const [lastBatchId, setLastBatchId] = useState<number | null>(null);
  /** 체크 ON: 이미 있는 일자는 건너뛰고 없는 일자만 저장. 체크 OFF: 엑셀에 있는 모든 일자를 삭제 후 다시 저장 */
  const [incrementalOnly, setIncrementalOnly] = useState(true);

  const uploadFile = useCallback(
    async (file: File) => {
      const name = file.name.toLowerCase();
      if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
        toast.error('.xlsx 또는 .xls 파일만 업로드할 수 있습니다.');
        setState('error');
        return;
      }
      setState('uploading');
      try {
        const res = await transactionApi.uploadExcel(file, incrementalOnly, !incrementalOnly);
        const data = res.data.data;
        const inserted = data?.inserted ?? 0;
        const txN = data?.transactionInserted ?? 0;
        const inN = data?.stockInInserted ?? 0;
        const batchId = data?.batchId;
        const overwritten = typeof data?.overwritten === 'number' ? data.overwritten : 0;
        const skippedDates = typeof data?.skippedDates === 'number' ? data.skippedDates : 0;
        const adjN = data?.adjustmentInserted ?? 0;
        const adjSkip = data?.adjustmentSkipped ?? 0;
        const adjDel = data?.adjustmentOverwrittenDeletes ?? 0;
        if (batchId != null) setLastBatchId(batchId);
        if (overwritten > 0) {
          toast.warning(
            `${overwritten.toLocaleString()}건의 기존 엑셀 판매·입고 데이터를 새 업로드로 덮어썼습니다. (동일 일자·유종의 엑셀 판매·입고만 해당)`,
          );
        }
        if (!incrementalOnly && adjDel > 0) {
          toast.warning(
            `${adjDel.toLocaleString()}건의 기존 엑셀 재고 조정을 새 업로드로 덮어썼습니다. (동일 일자·유종의 엑셀 조정만 해당)`,
          );
        }
        if (incrementalOnly && skippedDates > 0) {
          toast.message(
            `${skippedDates.toLocaleString()}개 행(일자)은 이미 엑셀 데이터가 있어 건너뛰었습니다.`,
          );
        }
        if (incrementalOnly && adjSkip > 0) {
          toast.message(`${adjSkip.toLocaleString()}건의 재고 조정은 이미 등록되어 건너뛰었습니다.`);
        }
        toast.success(
          `총 ${inserted.toLocaleString()}건 저장 (판매 ${txN.toLocaleString()}, 입고 ${inN.toLocaleString()}, 재고조정 ${adjN.toLocaleString()}).`,
        );
        setState('success');
        await onUploadSuccess?.();
      } catch (e) {
        setState('error');
        toast.error(getErrorMessage(e));
      } finally {
        setTimeout(() => setState('idle'), 800);
      }
    },
    [onUploadSuccess, incrementalOnly],
  );

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragOver(false);
      const f = e.dataTransfer.files?.[0];
      if (f) void uploadFile(f);
    },
    [uploadFile],
  );

  const onFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) void uploadFile(f);
    e.target.value = '';
  };

  return (
    <section>
      <h2 className="text-lg font-bold text-gray-800 mb-3">재고 엑셀 업로드</h2>
      <p className="mb-3 text-xs text-gray-600 leading-relaxed">
        한 파일로 <strong className="text-gray-800">판매·입고</strong>와 <strong className="text-gray-800">재고 조정</strong>을 함께 반영합니다.{' '}
        <strong className="text-gray-800">W(휘발유)·Y(경유)</strong> 열이 있으면 그날 <strong className="text-gray-800">마감 잔여량</strong>으로 인식해{' '}
        <code className="text-gray-700">tb_stock_adjustment</code>에 반영할 때는 장부 대비 실측 차이(physical − book)를 씁니다. W·Y가 비어 있으면 입고·판매만으로
        마감을 계산해 업로드 로그에 <strong className="text-gray-800">계산 마감</strong>·<strong className="text-gray-800">행 조정량</strong>(행 시작 잔량 − 마감)을
        남깁니다. X·Z 열은 계산에 사용하지 않으며, 결과는{' '}
        <Link to="/adjustments" className="text-blue-600 font-medium hover:underline">
          재고 조정
        </Link>{' '}
        화면에서 조회할 수 있습니다.
      </p>
      <label className="mb-3 flex cursor-pointer items-start gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={incrementalOnly}
          onChange={(e) => setIncrementalOnly(e.target.checked)}
          disabled={state === 'uploading'}
          className="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
          onClick={(e) => e.stopPropagation()}
        />
        <span>
          <span className="font-medium">신규 일자만 추가</span>
          <span className="block text-xs text-gray-500 mt-0.5">
            체크: 이미 저장된 일자·유종는 건너뛰고 없는 데이터만 저장합니다. 해제: 엑셀에 있는 모든 일자의 엑셀 데이터를 지운 뒤 다시 저장합니다.
          </span>
        </span>
      </label>
      <div
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') inputRef.current?.click();
        }}
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        className={[
          'rounded-2xl border-2 border-dashed p-8 text-center cursor-pointer transition-colors',
          dragOver ? 'border-blue-500 bg-blue-50' : 'border-gray-300 bg-white hover:border-blue-400 hover:bg-gray-50',
          state === 'uploading' ? 'pointer-events-none opacity-70' : '',
        ].join(' ')}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".xlsx,.xls"
          className="hidden"
          onChange={onFileChange}
          disabled={state === 'uploading'}
        />
        <p className="text-sm font-medium text-gray-700">
          {state === 'uploading' ? '업로드 중…' : '파일을 드래그하거나 클릭하여 선택'}
        </p>
        <p className="text-xs text-gray-500 mt-2">
          A열 날짜 · 휘발유 C·D·G·H · 경유 J·K·N·O (L·원) · 재고조정 W·X·Y·Z (L)
        </p>
        <div className="mt-4 flex flex-col sm:flex-row gap-2 sm:items-center sm:justify-center text-sm">
          {lastBatchId != null && (
            <Link
              to={`/excel-uploads?batchId=${lastBatchId}`}
              className="text-blue-600 font-semibold hover:underline"
              onClick={(e) => e.stopPropagation()}
            >
              방금 업로드 원본 보기 →
            </Link>
          )}
          <Link
            to="/excel-uploads"
            className="text-gray-600 hover:text-blue-600 hover:underline"
            onClick={(e) => e.stopPropagation()}
          >
            전체 업로드 이력
          </Link>
        </div>
      </div>
    </section>
  );
}
