import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactApexChart from 'react-apexcharts';
import dayjs from 'dayjs';
import { priceApi } from '../../services/api';
import { FUEL_CHART_COLORS } from '../../constants/chartColors';
import type { DailyPivotRow, PriceTimelinePoint } from '../../types';

type TimelineFuelFilter = 'ALL' | 'GASOLINE' | 'DIESEL';

const FUEL_LABEL: Record<'GASOLINE' | 'DIESEL', string> = {
  GASOLINE: '휘발유',
  DIESEL: '경유',
};

function formatPrice(value: number | null): string {
  if (value == null) return '-';
  return `₩${Math.round(Number(value)).toLocaleString()}`;
}

/** 전일 대비 변동에 따라 색을 바꾸는 판매차 셀.
 *  같음 = 중립 회색, 증가 = 파랑, 감소 = 빨강, 전일 데이터 없음 = 기본 회색 + 부호. */
function MarginCell({ value, prevValue }: { value: number | null; prevValue: number | null }) {
  if (value == null || !Number.isFinite(Number(value))) return <span>-</span>;
  const rounded = Math.round(Number(value));
  const sign = rounded > 0 ? '+' : '';
  const text = `${sign}₩${rounded.toLocaleString()}`;

  const prevRounded =
    prevValue != null && Number.isFinite(Number(prevValue)) ? Math.round(Number(prevValue)) : null;

  let cls = 'text-gray-700';
  let title: string | undefined;
  if (prevRounded == null) {
    cls = 'text-gray-700';
    title = '전일 데이터 없음';
  } else if (rounded === prevRounded) {
    cls = 'text-gray-500';
    title = '전일과 동일';
  } else if (rounded > prevRounded) {
    cls = 'text-blue-600';
    const delta = rounded - prevRounded;
    title = `전일 대비 +₩${delta.toLocaleString()}`;
  } else {
    cls = 'text-red-600';
    const delta = prevRounded - rounded;
    title = `전일 대비 -₩${delta.toLocaleString()}`;
  }

  return (
    <span className={cls} title={title}>
      {text}
    </span>
  );
}

function buildPivotRows(rows: PriceTimelinePoint[]): DailyPivotRow[] {
  const byDate = new Map<string, DailyPivotRow>();

  for (const row of rows) {
    let p = byDate.get(row.date);
    if (!p) {
      p = {
        date: row.date,
        gasBuyPrice: null,
        gasSellingPrice: null,
        gasDiff: null,
        dieBuyPrice: null,
        dieselSellingPrice: null,
        dieDiff: null,
      };
      byDate.set(row.date, p);
    }
    if (row.fuelType === 'GASOLINE') {
      p.gasBuyPrice = row.avgBuyPrice;
      p.gasSellingPrice = row.avgActualSellingPrice;
    } else {
      p.dieBuyPrice = row.avgBuyPrice;
      p.dieselSellingPrice = row.avgActualSellingPrice;
    }
  }

  const out: DailyPivotRow[] = Array.from(byDate.values()).map((p) => ({
    ...p,
    gasDiff:
      p.gasSellingPrice != null && p.gasBuyPrice != null ? p.gasSellingPrice - p.gasBuyPrice : null,
    dieDiff:
      p.dieselSellingPrice != null && p.dieBuyPrice != null ? p.dieselSellingPrice - p.dieBuyPrice : null,
  }));
  out.sort((a, b) => a.date.localeCompare(b.date));
  return out;
}

export default function PriceTimelineSection() {
  const [timelineFuelType, setTimelineFuelType] = useState<TimelineFuelFilter>('ALL');
  const [timelineFromDate, setTimelineFromDate] = useState(() => dayjs().subtract(1, 'month').format('YYYY-MM-DD'));
  const [timelineToDate, setTimelineToDate] = useState(() => dayjs().format('YYYY-MM-DD'));
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [timelineError, setTimelineError] = useState('');
  const [timelineRows, setTimelineRows] = useState<PriceTimelinePoint[]>([]);

  const fetchTimeline = useCallback(async () => {
    if (!timelineFromDate || !timelineToDate) return;
    setTimelineLoading(true);
    setTimelineError('');
    try {
      const res = await priceApi.getTimeline({
        from: timelineFromDate,
        to: timelineToDate,
      });
      setTimelineRows(res.data.data);
    } catch {
      setTimelineError('기간별 가격 이력 조회에 실패했습니다.');
    } finally {
      setTimelineLoading(false);
    }
  }, [timelineFromDate, timelineToDate]);

  useEffect(() => {
    void fetchTimeline();
  }, [fetchTimeline]);

  const groupedTimeline = useMemo(() => {
    const grouped: Record<'GASOLINE' | 'DIESEL', PriceTimelinePoint[]> = {
      GASOLINE: [],
      DIESEL: [],
    };
    timelineRows.forEach((row) => {
      grouped[row.fuelType].push(row);
    });
    return grouped;
  }, [timelineRows]);

  const pivotRows = useMemo(() => buildPivotRows(timelineRows), [timelineRows]);

  const chartFuelTypes = useMemo((): Array<'GASOLINE' | 'DIESEL'> => {
    if (timelineFuelType === 'ALL') return ['GASOLINE', 'DIESEL'];
    return [timelineFuelType];
  }, [timelineFuelType]);

  const renderTimelineChart = (fuelType: 'GASOLINE' | 'DIESEL', rows: PriceTimelinePoint[]) => {
    const categories = rows.map((row) => row.date);
    const series = [
      { name: '재고단가', data: rows.map((row) => row.avgBuyPrice) },
      { name: '실판매가', data: rows.map((row) => row.avgActualSellingPrice) },
    ];
    const palette = FUEL_CHART_COLORS[fuelType];
    const options: ApexCharts.ApexOptions = {
      chart: { type: 'line', toolbar: { show: false } },
      stroke: { curve: 'smooth', width: 2, dashArray: [4, 0] },
      dataLabels: { enabled: false },
      xaxis: { categories, labels: { rotate: -25 } },
      yaxis: {
        labels: {
          formatter: (value) => `₩${Math.round(value).toLocaleString()}`,
        },
      },
      tooltip: {
        y: {
          formatter: (value) => `₩${Math.round(value).toLocaleString()}`,
        },
      },
      colors: [palette.dark, palette.light],
      legend: { position: 'top' },
    };

    return (
      <div key={fuelType} className="bg-white rounded-2xl shadow p-5 border border-gray-100">
        <h3 className="font-bold text-gray-800 mb-3">{FUEL_LABEL[fuelType]} 가격 추이</h3>
        <ReactApexChart type="line" height={260} options={options} series={series} />
      </div>
    );
  };

  return (
    <section className="bg-white rounded-2xl shadow p-6 border border-gray-100 space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-gray-800 text-lg">유종별 일자 가격 이력</h2>
        <button
          type="button"
          onClick={() => void fetchTimeline()}
          disabled={timelineLoading}
          className="px-4 py-2 rounded-xl text-sm font-semibold bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {timelineLoading ? '조회 중...' : '조회'}
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div>
          <label className="label">차트 유종</label>
          <select
            value={timelineFuelType}
            onChange={(e) => setTimelineFuelType(e.target.value as TimelineFuelFilter)}
            className="input"
          >
            <option value="ALL">전체(휘발유+경유)</option>
            <option value="GASOLINE">휘발유</option>
            <option value="DIESEL">경유</option>
          </select>
        </div>
        <div>
          <label className="label">시작일</label>
          <input type="date" value={timelineFromDate} onChange={(e) => setTimelineFromDate(e.target.value)} className="input" />
        </div>
        <div>
          <label className="label">종료일</label>
          <input type="date" value={timelineToDate} onChange={(e) => setTimelineToDate(e.target.value)} className="input" />
        </div>
      </div>

      {timelineError && <div className="text-sm text-red-600">{timelineError}</div>}

      {timelineRows.length === 0 && !timelineLoading ? (
        <div className="text-sm text-gray-500 bg-gray-50 rounded-xl p-4">선택한 기간에 표시할 가격 데이터가 없습니다.</div>
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {chartFuelTypes
              .map((ft) => ({ ft, rows: groupedTimeline[ft] }))
              .filter(({ rows }) => rows.length > 0)
              .map(({ ft, rows }) => renderTimelineChart(ft, rows))}
          </div>

          <div className="overflow-x-auto border border-gray-100 rounded-xl">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 text-gray-600">
                <tr>
                  <th className="text-left px-4 py-2 whitespace-nowrap">일자</th>
                  <th className="text-left px-4 py-2 whitespace-nowrap">휘발유 재고단가</th>
                  <th className="text-left px-4 py-2 whitespace-nowrap">휘발유 실판매가</th>
                  <th className="text-left px-4 py-2 whitespace-nowrap">휘발유 판매차</th>
                  <th className="text-left px-4 py-2 whitespace-nowrap">경유 재고단가</th>
                  <th className="text-left px-4 py-2 whitespace-nowrap">경유 실판매가</th>
                  <th className="text-left px-4 py-2 whitespace-nowrap">경유 판매차</th>
                </tr>
              </thead>
              <tbody>
                {pivotRows.map((row, index) => {
                  const prev = index > 0 ? pivotRows[index - 1] : null;
                  return (
                    <tr key={row.date} className="border-t border-gray-100">
                      <td className="px-4 py-2 whitespace-nowrap">{row.date}</td>
                      <td className="px-4 py-2 text-left whitespace-nowrap">{formatPrice(row.gasBuyPrice)}</td>
                      <td className="px-4 py-2 text-left whitespace-nowrap">{formatPrice(row.gasSellingPrice)}</td>
                      <td className="px-4 py-2 text-left whitespace-nowrap">
                        <MarginCell value={row.gasDiff} prevValue={prev?.gasDiff ?? null} />
                      </td>
                      <td className="px-4 py-2 text-left whitespace-nowrap">{formatPrice(row.dieBuyPrice)}</td>
                      <td className="px-4 py-2 text-left whitespace-nowrap">{formatPrice(row.dieselSellingPrice)}</td>
                      <td className="px-4 py-2 text-left whitespace-nowrap">
                        <MarginCell value={row.dieDiff} prevValue={prev?.dieDiff ?? null} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
