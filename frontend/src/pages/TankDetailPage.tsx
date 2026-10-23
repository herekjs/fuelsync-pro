import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { tankApi, stockInApi } from '../services/api';
import { useWebSocket } from '../hooks/useWebSocket';
import StatusBadge from '../components/common/StatusBadge';
import ReactApexChart from 'react-apexcharts';
import dayjs from 'dayjs';
import type { Tank, StockIn, TankDailyRemainingPoint, TankStatus, StockUpdateMessage, FuelType } from '../types';

/** 일별 잔여량 추이 라인 — 경유 파랑, 휘발유·프리미엄 노랑 (대시보드 판매 차트와 동일 톤) */
const TANK_TREND_LINE_COLOR: Record<FuelType, string> = {
  GASOLINE: '#facc15',
  DIESEL: '#3b82f6',
  LPG: '#64748b',
  PREMIUM: '#facc15',
};

const FUEL_LABEL: Record<string, string> = {
  GASOLINE: '휘발유',
  DIESEL: '경유',
  LPG: 'LPG',
  PREMIUM: '프리미엄',
};

const STATUS_COLOR: Record<string, string> = {
  NORMAL: 'bg-green-500',
  WARNING: 'bg-yellow-400',
  DANGER: 'bg-red-500',
};

export default function TankDetailPage() {
  const { tankId } = useParams<{ tankId: string }>();
  const navigate = useNavigate();

  const [tank, setTank] = useState<Tank | null>(null);
  const [stockIns, setStockIns] = useState<StockIn[]>([]);
  const [trendFromDate, setTrendFromDate] = useState(() => dayjs().subtract(1, 'month').format('YYYY-MM-DD'));
  const [trendToDate, setTrendToDate] = useState(() => dayjs().format('YYYY-MM-DD'));
  const [trendRows, setTrendRows] = useState<TankDailyRemainingPoint[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendError, setTrendError] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchDailyTrend = useCallback(async () => {
    if (!tankId || !trendFromDate || !trendToDate) return;
    setTrendLoading(true);
    setTrendError('');
    try {
      const res = await tankApi.getDailyRemaining(Number(tankId), {
        from: trendFromDate,
        to: trendToDate,
      });
      setTrendRows(res.data.data);
    } catch {
      setTrendError('일별 잔여량 추이 조회에 실패했습니다.');
    } finally {
      setTrendLoading(false);
    }
  }, [tankId, trendFromDate, trendToDate]);

  useEffect(() => {
    if (!tankId) return;
    const id = Number(tankId);
    Promise.all([
      tankApi.getOne(id),
      stockInApi.getByTank(id),
      tankApi.getDailyRemaining(id, {
        from: trendFromDate,
        to: trendToDate,
      }),
    ])
      .then(([tankRes, stockRes, trendRes]) => {
        setTank(tankRes.data.data);
        setStockIns(stockRes.data.data);
        setTrendRows(trendRes.data.data);
      })
      .catch(() => setError('탱크 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false));
  }, [tankId]);

  // 실시간 재고 업데이트 반영
  useWebSocket({
    onStockUpdate: useCallback((msg: StockUpdateMessage) => {
      if (msg.tankId === Number(tankId)) {
        setTank((prev) =>
          prev
            ? {
                ...prev,
                currentVolume: msg.currentVolume,
                fillRatio: msg.fillRatio,
                status: msg.status as TankStatus,
              }
            : prev,
        );
      }
    }, [tankId]),
  });

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }

  if (error || !tank) {
    return (
      <div className="p-6 max-w-3xl mx-auto">
        <button onClick={() => navigate(-1)} className="text-blue-600 hover:underline text-sm mb-4 inline-flex items-center gap-1">
          ← 뒤로
        </button>
        <div className="bg-red-50 border border-red-200 rounded-2xl p-6 text-red-700">
          {error || '탱크를 찾을 수 없습니다.'}
        </div>
      </div>
    );
  }

  const fillRatio = Number(tank.fillRatio);
  const trendCategories = trendRows.map((row) => row.date);
  const trendSeries = [
    { name: '일별 마감 잔여량(L)', data: trendRows.map((row) => Number(row.endVolume)) },
  ];
  const trendOptions: ApexCharts.ApexOptions = {
    chart: { type: 'line', toolbar: { show: false } },
    stroke: { curve: 'smooth', width: 2 },
    dataLabels: { enabled: false },
    xaxis: { categories: trendCategories, labels: { rotate: -25 } },
    yaxis: {
      labels: {
        formatter: (value) => Math.round(value).toLocaleString(),
      },
    },
    tooltip: {
      y: {
        formatter: (value) => `${Math.round(value).toLocaleString()} L`,
      },
    },
    colors: [TANK_TREND_LINE_COLOR[tank.fuelType]],
  };

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate(-1)}
          className="text-blue-600 hover:underline text-sm inline-flex items-center gap-1"
        >
          ← 뒤로
        </button>
        <h1 className="text-2xl font-bold text-gray-900">{tank.tankName}</h1>
        <StatusBadge status={tank.status} />
      </div>

      {/* 탱크 정보 카드 */}
      <div className="bg-white rounded-2xl shadow border border-gray-100 p-6">
        <div className="grid grid-cols-2 md:grid-cols-3 gap-6">
          <InfoItem label="유종" value={FUEL_LABEL[tank.fuelType] ?? tank.fuelType} />
          <InfoItem label="최대 용량" value={`${Number(tank.maxCapacity).toLocaleString()} L`} />
          <InfoItem
            label="현재 재고"
            value={`${Number(tank.currentVolume).toLocaleString()} L`}
            highlight
          />
          <InfoItem label="주의 임계값" value={`${tank.thresholdWarning}%`} />
          <InfoItem label="위험 임계값" value={`${tank.thresholdDanger}%`} />
          <InfoItem
            label="최근 업데이트"
            value={dayjs(tank.updatedAt).format('MM-DD HH:mm')}
          />
          {tank.temperature != null && (
            <InfoItem label="온도" value={`${tank.temperature}°C`} />
          )}
          {tank.waterLevel != null && (
            <InfoItem label="수분량" value={`${tank.waterLevel} mm`} />
          )}
        </div>

        {/* 잔량 게이지 */}
        <div className="mt-6">
          <div className="flex justify-between text-sm text-gray-600 mb-1">
            <span>잔량 비율</span>
            <span className="font-semibold">{fillRatio.toFixed(1)}%</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-4 overflow-hidden">
            <div
              className={`h-4 rounded-full transition-all duration-500 ${STATUS_COLOR[tank.status]}`}
              style={{ width: `${Math.min(fillRatio, 100)}%` }}
            />
          </div>
          <div className="flex justify-between text-xs text-gray-400 mt-1">
            <span>0 L</span>
            <span>{Number(tank.maxCapacity).toLocaleString()} L</span>
          </div>
        </div>
      </div>

      <section className="bg-white rounded-2xl shadow border border-gray-100 p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-800">일별 잔여량 추이</h2>
          <button
            type="button"
            onClick={() => void fetchDailyTrend()}
            disabled={trendLoading}
            className="px-4 py-2 rounded-xl text-sm font-semibold bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {trendLoading ? '조회 중...' : '조회'}
          </button>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="label">시작일</label>
            <input type="date" value={trendFromDate} onChange={(e) => setTrendFromDate(e.target.value)} className="input" />
          </div>
          <div>
            <label className="label">종료일</label>
            <input type="date" value={trendToDate} onChange={(e) => setTrendToDate(e.target.value)} className="input" />
          </div>
        </div>
        {trendError && <div className="text-sm text-red-600">{trendError}</div>}

        {trendRows.length === 0 ? (
          <div className="text-sm text-gray-500 bg-gray-50 rounded-xl p-4">선택한 기간에 표시할 잔여량 데이터가 없습니다.</div>
        ) : (
          <div className="space-y-4">
            <ReactApexChart type="line" height={260} options={trendOptions} series={trendSeries} />
            <div className="overflow-x-auto border border-gray-100 rounded-xl">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 text-gray-600">
                  <tr>
                    <th className="text-left px-4 py-2">일자</th>
                    <th className="text-left px-4 py-2">입고량(L)</th>
                    <th className="text-left px-4 py-2">판매량(L)</th>
                    <th className="text-left px-4 py-2">조정량(L)</th>
                    <th className="text-left px-4 py-2">마감 잔여량(L)</th>
                  </tr>
                </thead>
                <tbody>
                  {trendRows.map((row) => (
                    <tr key={row.date} className="border-t border-gray-100">
                      <td className="px-4 py-2">{row.date}</td>
                      <td className="px-4 py-2 text-left">{Number(row.inVolume).toLocaleString()}</td>
                      <td className="px-4 py-2 text-left">{Number(row.soldVolume).toLocaleString()}</td>
                      <td className="px-4 py-2 text-left">{Number(row.adjustmentVolume).toLocaleString()}</td>
                      <td className="px-4 py-2 text-left font-semibold text-blue-700">{Number(row.endVolume).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>

      {/* 입고 이력 */}
      <section>
        <h2 className="text-lg font-bold text-gray-800 mb-3">입고 이력</h2>
        <div className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                {['입고량(L)', '단가(원)', '총 원가', '공급사', '입고일'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {stockIns.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                    입고 이력이 없습니다.
                  </td>
                </tr>
              ) : (
                stockIns.map((s) => (
                  <tr key={s.inId} className="hover:bg-gray-50">
                    <td className="px-4 py-3">{Number(s.inVolume).toLocaleString()}</td>
                    <td className="px-4 py-3">₩{Math.round(Number(s.buyPrice)).toLocaleString()}</td>
                    <td className="px-4 py-3 font-medium text-blue-700">
                      ₩{Math.round(Number(s.totalCost)).toLocaleString()}
                    </td>
                    <td className="px-4 py-3">{s.vendorName ?? '-'}</td>
                    <td className="px-4 py-3">{s.inDate}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function InfoItem({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div>
      <p className="text-xs text-gray-500 mb-0.5">{label}</p>
      <p className={`text-base font-semibold ${highlight ? 'text-blue-700' : 'text-gray-900'}`}>
        {value}
      </p>
    </div>
  );
}
