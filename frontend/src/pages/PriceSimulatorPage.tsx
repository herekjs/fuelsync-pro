import { useState } from 'react';
import PriceTimelineSection from '../components/price/PriceTimelineSection';
import { competitorApi, priceApi } from '../services/api';
import type { FuelType, PriceSimulateResponse } from '../types';

export default function PriceSimulatorPage() {
  const [form, setForm] = useState({
    fuelType: 'GASOLINE' as FuelType,
    avgBuyPrice: '',
    targetMarginRate: '10',
    competitorAvgPrice: '',
  });
  const [result, setResult] = useState<PriceSimulateResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [fetchingCompetitor, setFetchingCompetitor] = useState(false);
  const [radius, setRadius] = useState(3);
  const [errorMessage, setErrorMessage] = useState('');

  const handleSimulate = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setErrorMessage('');
    try {
      const res = await priceApi.simulate({
        fuelType: form.fuelType,
        avgBuyPrice: Number(form.avgBuyPrice),
        targetMarginRate: Number(form.targetMarginRate),
        competitorAvgPrice: form.competitorAvgPrice ? Number(form.competitorAvgPrice) : undefined,
      });
      setResult(res.data.data);
    } finally {
      setLoading(false);
    }
  };

  const handleFetchCompetitorAverage = async () => {
    setFetchingCompetitor(true);
    setErrorMessage('');
    try {
      const res = await competitorApi.getAround(form.fuelType, radius);
      const prices = res.data.data
        .map((x) => x.price)
        .filter((x): x is number => x != null);

      if (prices.length === 0) {
        setErrorMessage('경쟁사 판매가 데이터가 없어 평균을 계산할 수 없습니다.');
        return;
      }

      const avg = Math.round(prices.reduce((acc, cur) => acc + cur, 0) / prices.length);
      setForm((prev) => ({ ...prev, competitorAvgPrice: String(avg) }));
    } catch {
      setErrorMessage('경쟁사 평균가 조회에 실패했습니다. 정보 관리에서 위치를 먼저 등록해 주세요.');
    } finally {
      setFetchingCompetitor(false);
    }
  };

  const diff = result?.priceDiffFromCompetitor;
  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">유가 이력/시뮬레이터</h1>
      <PriceTimelineSection />

      <form onSubmit={handleSimulate} className="bg-white rounded-2xl shadow p-6 border border-gray-100 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="label">유종</label>
            <select value={form.fuelType}
              onChange={(e) => setForm({ ...form, fuelType: e.target.value as FuelType })}
              className="input">
              <option value="GASOLINE">휘발유</option>
              <option value="DIESEL">경유</option>
            </select>
          </div>
          <div>
            <label className="label">평균 매입 단가 (원/L)</label>
            <input type="number" required min="0.01" step="0.01" value={form.avgBuyPrice}
              onChange={(e) => setForm({ ...form, avgBuyPrice: e.target.value })}
              placeholder="예: 1480.00" className="input" />
          </div>
          <div>
            <label className="label">목표 마진율 (%)</label>
            <input type="number" required min="0.1" max="99.99" step="0.1" value={form.targetMarginRate}
              onChange={(e) => setForm({ ...form, targetMarginRate: e.target.value })}
              className="input" />
          </div>
          <div>
            <label className="label">경쟁사 평균 판매가 (원/L)</label>
            <input type="number" min="0.01" step="0.01" value={form.competitorAvgPrice}
              onChange={(e) => setForm({ ...form, competitorAvgPrice: e.target.value })}
              placeholder="오피넷 기준 (선택)" className="input" />
            <div className="mt-2 flex items-center gap-2">
              <select
                className="input !w-28"
                value={radius}
                onChange={(e) => setRadius(Number(e.target.value))}
              >
                {[1, 2, 3, 4, 5].map((r) => (
                  <option key={r} value={r}>{r}km</option>
                ))}
              </select>
              <button
                type="button"
                onClick={handleFetchCompetitorAverage}
                disabled={fetchingCompetitor}
                className="px-3 py-2 rounded-xl text-xs font-semibold bg-indigo-100 text-indigo-700 hover:bg-indigo-200 disabled:opacity-50"
              >
                {fetchingCompetitor ? '조회 중...' : '경쟁사 평균 가져오기'}
              </button>
            </div>
          </div>
        </div>
        {errorMessage && (
          <div className="text-sm text-red-600">{errorMessage}</div>
        )}
        <button type="submit" disabled={loading}
          className="w-full bg-indigo-600 text-white rounded-xl py-2.5 font-semibold hover:bg-indigo-700 disabled:opacity-50">
          {loading ? '계산 중...' : '권장 판매가 계산'}
        </button>
      </form>

      {result && (
        <div className="bg-white rounded-2xl shadow p-6 border border-gray-100 space-y-4">
          <h2 className="font-bold text-gray-800 text-lg">시뮬레이션 결과</h2>
          <div className="flex items-center gap-4">
            <div className="bg-indigo-50 rounded-xl p-5 flex-1 text-center">
              <p className="text-xs text-indigo-600 font-medium mb-1">권장 판매가</p>
              <p className="text-3xl font-extrabold text-indigo-700">
                ₩{Number(result.suggestedPrice).toLocaleString()}
              </p>
              <p className="text-xs text-gray-400 mt-1">리터당 (원)</p>
            </div>
            {diff != null && (
              <div className={`rounded-xl p-5 flex-1 text-center ${diff > 0 ? 'bg-red-50' : 'bg-green-50'}`}>
                <p className={`text-xs font-medium mb-1 ${diff > 0 ? 'text-red-600' : 'text-green-600'}`}>
                  경쟁사 대비
                </p>
                <p className={`text-3xl font-extrabold ${diff > 0 ? 'text-red-700' : 'text-green-700'}`}>
                  {diff > 0 ? '+' : ''}{Number(diff).toLocaleString()}원
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  경쟁사: ₩{Number(result.competitorAvgPrice).toLocaleString()}
                </p>
              </div>
            )}
          </div>
          <div className="grid grid-cols-3 gap-3 text-sm text-gray-600">
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-xs text-gray-400">매입 단가</p>
              <p className="font-semibold mt-0.5">₩{Number(result.avgBuyPrice).toLocaleString()}</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-xs text-gray-400">목표 마진율</p>
              <p className="font-semibold mt-0.5">{result.targetMarginRate}%</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-xs text-gray-400">리터당 마진</p>
              <p className="font-semibold mt-0.5 text-green-700">
                ₩{(Number(result.suggestedPrice) - Number(result.avgBuyPrice)).toFixed(2)}
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
