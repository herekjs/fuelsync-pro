import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { adjustmentApi, tankApi } from '../services/api';
import type { AdjustmentInputType, StockAdjustment, Tank } from '../types';
import dayjs from 'dayjs';

const INPUT_TYPE_LABELS: Record<AdjustmentInputType, string> = {
  WEB: '웹',
  EXCEL: '엑셀',
};

function formatDiffLiter(n: number): string {
  const r = Math.round(n);
  return `${r > 0 ? '+' : ''}${r.toLocaleString()}`;
}

function formatDiffAmountWon(n: number): string {
  const r = Math.round(n);
  const sign = r > 0 ? '+' : r < 0 ? '-' : '';
  return `${sign}₩${Math.abs(r).toLocaleString()}`;
}

export default function AdjustmentPage() {
  const [tanks, setTanks] = useState<Tank[]>([]);
  const [adjustments, setAdjustments] = useState<StockAdjustment[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState({
    tankId: '',
    bookStock: '',
    physicalStock: '',
    adjDate: dayjs().format('YYYY-MM-DD'),
    memo: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [fromDate, setFromDate] = useState(() => dayjs().subtract(1, 'month').format('YYYY-MM-DD'));
  const [toDate, setToDate] = useState(() => dayjs().format('YYYY-MM-DD'));

  const fetchAdjustments = async (from: string, to: string) => {
    const res = await adjustmentApi.getByPeriod(from, to);
    setAdjustments(res.data.data);
  };

  useEffect(() => {
    tankApi.getAll().then((res) => setTanks(res.data.data));
    fetchAdjustments(fromDate, toDate);
  }, []);

  const handleSearch = () => {
    fetchAdjustments(fromDate, toDate).catch(() => {});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await adjustmentApi.create({
        tankId: Number(form.tankId),
        bookStock: Number(form.bookStock),
        physicalStock: Number(form.physicalStock),
        adjDate: form.adjDate,
        memo: form.memo || undefined,
      });
      await fetchAdjustments(fromDate, toDate);
      setForm({
        tankId: '',
        bookStock: '',
        physicalStock: '',
        adjDate: dayjs().format('YYYY-MM-DD'),
        memo: '',
      });
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        '재고 조정 등록에 실패했습니다.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const diffVolume =
    form.bookStock && form.physicalStock
      ? Number(form.physicalStock) - Number(form.bookStock)
      : null;

  const diffPreviewClass =
    diffVolume === null
      ? 'text-gray-700 bg-gray-50'
      : diffVolume > 0
        ? 'text-blue-600 bg-blue-50'
        : diffVolume < 0
          ? 'text-red-600 bg-red-50'
          : 'text-gray-700 bg-gray-50';

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">재고 조정</h1>

      <p className="text-sm text-gray-600">
        엑셀 W·X(휘발유)·Y·Z(경유) 재고 조정은{' '}
        <Link to="/" className="text-blue-600 font-medium hover:underline">
          대시보드
        </Link>
        의 「재고 엑셀 업로드」에서 통합 파일을 올리면 함께 저장됩니다.
      </p>

      <div className="bg-white rounded-2xl shadow border border-gray-100">
        <button
          type="button"
          onClick={() => setFormOpen((prev) => !prev)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-semibold text-gray-700 hover:bg-gray-50 rounded-2xl transition-colors"
        >
          <span>재고 조정 등록</span>
          <span className="text-gray-400 text-lg">{formOpen ? '▲' : '▼'}</span>
        </button>

        {formOpen && (
          <form onSubmit={handleSubmit} className="px-6 pb-6 space-y-4 border-t border-gray-100 pt-4">
            <p className="text-xs text-gray-500">
              실측 재고를 입력하면 최근 3개월 평균 매입단가 기준으로 차이금액이 자동 계산됩니다. 동일 탱크·동일 날짜 중복
              등록은 불가합니다.
            </p>
            {error && <div className="text-red-600 text-sm bg-red-50 rounded-lg p-3">{error}</div>}
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
              <Field label="탱크 선택">
                <select
                  required
                  value={form.tankId}
                  onChange={(e) => setForm({ ...form, tankId: e.target.value })}
                  className="input"
                >
                  <option value="">선택</option>
                  {tanks.map((t) => (
                    <option key={t.tankId} value={t.tankId}>
                      {t.tankName} ({t.fuelType})
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="장부 재고 (L)">
                <input
                  type="number"
                  required
                  min="0"
                  step="0.01"
                  value={form.bookStock}
                  onChange={(e) => setForm({ ...form, bookStock: e.target.value })}
                  className="input"
                  placeholder="시스템 기록 재고량"
                />
              </Field>
              <Field label="실측 재고 (L)">
                <input
                  type="number"
                  required
                  min="0"
                  step="0.01"
                  value={form.physicalStock}
                  onChange={(e) => setForm({ ...form, physicalStock: e.target.value })}
                  className="input"
                  placeholder="실제 측정 재고량"
                />
              </Field>
              <Field label="조정일">
                <input
                  type="date"
                  required
                  value={form.adjDate}
                  onChange={(e) => setForm({ ...form, adjDate: e.target.value })}
                  className="input"
                />
              </Field>
              <Field label="메모">
                <input
                  type="text"
                  value={form.memo}
                  onChange={(e) => setForm({ ...form, memo: e.target.value })}
                  className="input"
                  placeholder="선택 사항"
                />
              </Field>
              {diffVolume !== null && (
                <Field label="차이량 (실측 - 장부)">
                  <div className={`input font-semibold ${diffPreviewClass}`}>
                    {formatDiffLiter(diffVolume)} L
                  </div>
                </Field>
              )}
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-blue-600 text-white rounded-xl py-2.5 font-semibold hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {submitting ? '등록 중...' : '재고 조정 등록'}
            </button>
          </form>
        )}
      </div>

      <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
        <div className="flex flex-wrap items-end gap-3">
          <Field label="조회 시작일">
            <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="input" />
          </Field>
          <Field label="조회 종료일">
            <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} className="input" />
          </Field>
          <button
            onClick={handleSearch}
            className="px-5 py-2 bg-gray-700 text-white rounded-xl text-sm font-medium hover:bg-gray-800 transition-colors"
          >
            조회
          </button>
        </div>
      </div>

      <div className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              {['조정일', '탱크', '유종', '장부재고(L)', '실측재고(L)', '차이량(L)', '차이금액(원)', '입력', '메모'].map(
                (h) => (
                  <th key={h} className="px-4 py-3 text-left font-medium">
                    {h}
                  </th>
                ),
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {adjustments.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-4 py-8 text-center text-gray-400">
                  해당 기간에 재고 조정 내역이 없습니다.
                </td>
              </tr>
            ) : (
              adjustments.map((adj) => {
                const diff = Number(adj.diffVolume);
                const diffAmt = Number(adj.diffAmount);
                const diffClass =
                  diff > 0 ? 'text-blue-600' : diff < 0 ? 'text-red-600' : 'text-gray-600';
                const inputType = adj.inputType ?? 'WEB';
                return (
                  <tr key={adj.adjId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-700 whitespace-nowrap">{adj.adjDate}</td>
                    <td className="px-4 py-3 font-medium">{adj.tankName}</td>
                    <td className="px-4 py-3">{adj.fuelType}</td>
                    <td className="px-4 py-3">{Number(adj.bookStock).toLocaleString()}</td>
                    <td className="px-4 py-3">{Number(adj.physicalStock).toLocaleString()}</td>
                    <td className={`px-4 py-3 font-semibold ${diffClass}`}>{formatDiffLiter(diff)}</td>
                    <td className={`px-4 py-3 font-medium ${diffClass}`}>{formatDiffAmountWon(diffAmt)}</td>
                    <td className="px-4 py-3">
                      <span
                        className={
                          inputType === 'EXCEL'
                            ? 'px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800'
                            : 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600'
                        }
                      >
                        {INPUT_TYPE_LABELS[inputType]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500">{adj.memo ?? '-'}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
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
