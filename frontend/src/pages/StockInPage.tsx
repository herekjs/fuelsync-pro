import { useState, useEffect, useCallback } from 'react';
import { stockInApi, tankApi } from '../services/api';
import type { StockIn, Tank } from '../types';
import dayjs from 'dayjs';

export default function StockInPage() {
  const [tanks, setTanks] = useState<Tank[]>([]);
  const [stockIns, setStockIns] = useState<StockIn[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState({
    tankId: '',
    inVolume: '',
    buyPrice: '',
    vendorName: '',
    inDate: dayjs().format('YYYY-MM-DD'),
    memo: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [fromDate, setFromDate] = useState(() => dayjs().subtract(1, 'month').format('YYYY-MM-DD'));
  const [toDate, setToDate] = useState(() => dayjs().format('YYYY-MM-DD'));

  const fetchStockIns = useCallback(async (from: string, to: string) => {
    const res = await stockInApi.getByPeriod(from, to);
    setStockIns(res.data.data);
  }, []);

  useEffect(() => {
    tankApi.getAll().then((res) => setTanks(res.data.data));
  }, []);

  useEffect(() => {
    fetchStockIns(fromDate, toDate).catch(() => {});
  }, [fromDate, toDate, fetchStockIns]);

  const handleSearch = () => {
    fetchStockIns(fromDate, toDate).catch(() => {});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await stockInApi.create({
        tankId: Number(form.tankId),
        inVolume: Number(form.inVolume),
        buyPrice: Number(form.buyPrice),
        vendorName: form.vendorName || undefined,
        inDate: form.inDate,
        memo: form.memo || undefined,
      });
      await fetchStockIns(fromDate, toDate);
      setForm({ tankId: '', inVolume: '', buyPrice: '', vendorName: '', inDate: dayjs().format('YYYY-MM-DD'), memo: '' });
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? '입고 등록에 실패했습니다.';
      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">입고 관리</h1>

      <div className="bg-white rounded-2xl shadow border border-gray-100">
        <button
          type="button"
          onClick={() => setFormOpen((prev) => !prev)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-semibold text-gray-700 hover:bg-gray-50 rounded-2xl transition-colors"
        >
          <span>입고 등록</span>
          <span className="text-gray-400 text-lg">{formOpen ? '▲' : '▼'}</span>
        </button>

        {formOpen && (
          <form onSubmit={handleSubmit} className="px-6 pb-6 space-y-4 border-t border-gray-100 pt-4">
            {error && <div className="text-red-600 text-sm bg-red-50 rounded p-3">{error}</div>}
            <div className="grid grid-cols-2 gap-4">
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
              <Field label="입고량 (L)">
                <input type="number" required min="0.01" step="0.01" value={form.inVolume}
                  onChange={(e) => setForm({ ...form, inVolume: e.target.value })} className="input" />
              </Field>
              <Field label="리터당 매입 단가 (원)">
                <input type="number" required min="0.01" step="0.01" value={form.buyPrice}
                  onChange={(e) => setForm({ ...form, buyPrice: e.target.value })} className="input" />
              </Field>
              <Field label="공급사명">
                <input type="text" value={form.vendorName}
                  onChange={(e) => setForm({ ...form, vendorName: e.target.value })} className="input" />
              </Field>
              <Field label="입고일">
                <input type="date" required value={form.inDate}
                  onChange={(e) => setForm({ ...form, inDate: e.target.value })} className="input" />
              </Field>
              <Field label="메모">
                <input type="text" value={form.memo}
                  onChange={(e) => setForm({ ...form, memo: e.target.value })} className="input" />
              </Field>
            </div>
            <button type="submit" disabled={submitting}
              className="w-full bg-blue-600 text-white rounded-xl py-2.5 font-semibold hover:bg-blue-700 disabled:opacity-50">
              {submitting ? '등록 중...' : '입고 등록'}
            </button>
          </form>
        )}
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
        </div>
      </div>

      <div className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              {['입고일', '탱크', '유종', '입고량(L)', '단가(원)', '총 원가', '공급사'].map((h) => (
                <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {stockIns.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-gray-400">
                  해당 기간에 입고 내역이 없습니다.
                </td>
              </tr>
            ) : (
              stockIns.map((s) => (
                <tr key={s.inId} className="hover:bg-gray-50">
                  <td className="px-4 py-3">{s.inDate}</td>
                  <td className="px-4 py-3 font-medium">{s.tankName}</td>
                  <td className="px-4 py-3">{s.fuelType}</td>
                  <td className="px-4 py-3">{Number(s.inVolume).toLocaleString()}</td>
                  <td className="px-4 py-3">₩{Math.round(Number(s.buyPrice)).toLocaleString()}</td>
                  <td className="px-4 py-3 font-medium text-blue-700">₩{Math.round(Number(s.totalCost)).toLocaleString()}</td>
                  <td className="px-4 py-3">{s.vendorName ?? '-'}</td>
                </tr>
              ))
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
