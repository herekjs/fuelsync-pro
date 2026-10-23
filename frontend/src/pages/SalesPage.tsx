import { useState, useEffect, useCallback } from 'react';
import { transactionApi, tankApi } from '../services/api';
import type { Transaction, Tank, PaymentType, TransactionInputType } from '../types';
import dayjs from 'dayjs';

const PAYMENT_LABELS: Record<PaymentType, string> = {
  CARD: '카드',
  CASH: '현금',
  APP: '앱',
};

const INPUT_TYPE_LABELS: Record<TransactionInputType, string> = {
  WEB: '웹',
  EXCEL: '엑셀',
};

function paymentTypeLabel(p: PaymentType | null | undefined): string {
  if (p == null) return '-';
  return PAYMENT_LABELS[p];
}

export default function SalesPage() {
  const [tanks, setTanks] = useState<Tank[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState({
    tankId: '',
    liter: '',
    unitPrice: '',
    paymentType: 'CARD',
    approvalNo: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // 기간 조회용 상태 (LocalDateTime 형식으로 백엔드 전달)
  const [fromDate, setFromDate] = useState(() => dayjs().subtract(1, 'month').format('YYYY-MM-DD'));
  const [toDate, setToDate] = useState(() => dayjs().format('YYYY-MM-DD'));

  const fetchTransactions = useCallback(async (from: string, to: string) => {
    const fromDt = `${from}T00:00:00`;
    const toDt = `${to}T23:59:59`;
    const res = await transactionApi.getByPeriod(fromDt, toDt);
    setTransactions(res.data.data);
  }, []);

  useEffect(() => {
    tankApi.getAll().then((res) => setTanks(res.data.data));
  }, []);

  useEffect(() => {
    fetchTransactions(fromDate, toDate).catch(() => {});
  }, [fromDate, toDate, fetchTransactions]);

  const handleSearch = () => {
    fetchTransactions(fromDate, toDate).catch(() => {});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await transactionApi.create({
        tankId: Number(form.tankId),
        liter: Number(form.liter),
        unitPrice: Number(form.unitPrice),
        paymentType: form.paymentType,
        approvalNo: form.approvalNo || undefined,
      });
      await fetchTransactions(fromDate, toDate);
      setForm({ tankId: '', liter: '', unitPrice: '', paymentType: 'CARD', approvalNo: '' });
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        '판매 등록에 실패했습니다.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const totalAmount = transactions.reduce((sum, tx) => sum + Number(tx.totalAmount), 0);
  const totalLiter = transactions.reduce((sum, tx) => sum + Number(tx.liter), 0);

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">판매 관리</h1>

      {/* 판매 등록 폼 */}
      <div className="bg-white rounded-2xl shadow border border-gray-100">
        <button
          type="button"
          onClick={() => setFormOpen((prev) => !prev)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-semibold text-gray-700 hover:bg-gray-50 rounded-2xl transition-colors"
        >
          <span>판매 등록</span>
          <span className="text-gray-400 text-lg">{formOpen ? '▲' : '▼'}</span>
        </button>

        {formOpen && (
          <form onSubmit={handleSubmit} className="px-6 pb-6 space-y-4 border-t border-gray-100 pt-4">
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
              <Field label="판매량 (L)">
                <input
                  type="number" required min="0.01" step="0.01"
                  value={form.liter}
                  onChange={(e) => setForm({ ...form, liter: e.target.value })}
                  className="input"
                />
              </Field>
              <Field label="리터당 판매 단가 (원)">
                <input
                  type="number" required min="1" step="1"
                  value={form.unitPrice}
                  onChange={(e) => setForm({ ...form, unitPrice: e.target.value })}
                  className="input"
                />
              </Field>
              <Field label="결제 수단">
                <select
                  value={form.paymentType}
                  onChange={(e) => setForm({ ...form, paymentType: e.target.value })}
                  className="input"
                >
                  <option value="CARD">카드</option>
                  <option value="CASH">현금</option>
                  <option value="APP">앱</option>
                </select>
              </Field>
              <Field label="승인번호 (선택)">
                <input
                  type="text"
                  value={form.approvalNo}
                  onChange={(e) => setForm({ ...form, approvalNo: e.target.value })}
                  className="input"
                  placeholder="카드 승인번호"
                />
              </Field>
              {form.liter && form.unitPrice && (
                <Field label="예상 판매 금액">
                  <div className="input bg-gray-50 text-blue-700 font-semibold">
                    ₩{(Number(form.liter) * Number(form.unitPrice)).toLocaleString()}
                  </div>
                </Field>
              )}
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-blue-600 text-white rounded-xl py-2.5 font-semibold hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {submitting ? '등록 중...' : '판매 등록'}
            </button>
          </form>
        )}
      </div>

      {/* 기간 조회 */}
      <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
        <div className="flex flex-wrap items-end gap-3">
          <Field label="조회 시작일">
            <input
              type="date" value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="input"
            />
          </Field>
          <Field label="조회 종료일">
            <input
              type="date" value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="input"
            />
          </Field>
          <button
            onClick={handleSearch}
            className="px-5 py-2 bg-gray-700 text-white rounded-xl text-sm font-medium hover:bg-gray-800 transition-colors"
          >
            조회
          </button>
          <div className="ml-auto flex gap-4 text-sm text-gray-600">
            <span>총 <strong className="text-gray-900">{totalLiter.toLocaleString()}L</strong> 판매</span>
            <span>총 매출 <strong className="text-blue-700">₩{totalAmount.toLocaleString()}</strong></span>
          </div>
        </div>
      </div>

      {/* 판매 목록 */}
      <div className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              {['일시', '탱크', '유종', '판매량(L)', '단가(원)', '총액(원)', '결제', '입력', '승인번호'].map((h) => (
                <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {transactions.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-4 py-8 text-center text-gray-400">
                  해당 기간에 판매 내역이 없습니다.
                </td>
              </tr>
            ) : (
              transactions.map((tx) => (
                <tr key={tx.txId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-500">
                    {dayjs(tx.txDatetime).format('MM-DD HH:mm')}
                  </td>
                  <td className="px-4 py-3 font-medium">{tx.tankName}</td>
                  <td className="px-4 py-3">{tx.fuelType}</td>
                  <td className="px-4 py-3">{Number(tx.liter).toLocaleString()}</td>
                  <td className="px-4 py-3">₩{Number(tx.unitPrice).toLocaleString()}</td>
                  <td className="px-4 py-3 font-medium text-blue-700">₩{Number(tx.totalAmount).toLocaleString()}</td>
                  <td className="px-4 py-3">
                    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700">
                      {paymentTypeLabel(tx.paymentType)}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        tx.inputType === 'EXCEL'
                          ? 'px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800'
                          : 'px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600'
                      }
                    >
                      {INPUT_TYPE_LABELS[tx.inputType ?? 'WEB']}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">{tx.approvalNo ?? '-'}</td>
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
