import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactApexChart from 'react-apexcharts';
import dayjs from 'dayjs';
import { toast } from 'sonner';
import { dashboardApi } from '../../services/api';
import type { SalesStatsDayOfWeek, SalesStatsResponse } from '../../types';
import { COLOR_DIESEL, COLOR_GASOLINE, FUEL_CHART_COLORS } from '../../constants/chartColors';

const DOW_LABELS = ['월', '화', '수', '목', '금', '토', '일'];

const SERIES_VOLUME = '판매량 (L)';
const SERIES_DIFF = '판매차 (L)';

function formatISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function parseISODate(s: string): Date {
  const [y, mo, da] = s.split('-').map(Number);
  return new Date(y, mo - 1, da);
}

/** 최근 1개월 (시작일) */
function defaultStartDate(): string {
  return dayjs().subtract(1, 'month').format('YYYY-MM-DD');
}

/** 오늘 (종료일) */
function defaultEndDate(): string {
  return dayjs().format('YYYY-MM-DD');
}

/** 시작~종료(포함) 모든 날짜 yyyy-MM-dd */
function eachDateInRange(start: string, end: string): string[] {
  const out: string[] = [];
  let cur = parseISODate(start);
  const endD = parseISODate(end);
  while (cur <= endD) {
    out.push(formatISODate(cur));
    cur = new Date(cur.getFullYear(), cur.getMonth(), cur.getDate() + 1);
  }
  return out;
}

function emptyDayOfWeek(): SalesStatsDayOfWeek[] {
  return Array.from({ length: 7 }, (_, i) => ({
    dayOfWeek: i,
    gasoline: 0,
    diesel: 0,
  }));
}

function normalizeDayOfWeek(stats: SalesStatsResponse | null): SalesStatsDayOfWeek[] {
  const raw = stats?.dayOfWeek;
  if (!raw || raw.length === 0) {
    return emptyDayOfWeek();
  }
  if (raw.length === 7) {
    return raw;
  }
  const byDow = new Map(raw.map((d) => [d.dayOfWeek, d]));
  return Array.from({ length: 7 }, (_, i) => byDow.get(i) ?? { dayOfWeek: i, gasoline: 0, diesel: 0 });
}

type ChartDateFilterBlockProps = {
  title: string;
  description: string;
  draftStart: string;
  draftEnd: string;
  onDraftStartChange: (v: string) => void;
  onDraftEndChange: (v: string) => void;
  onSearch: () => void;
  showLoading: boolean;
};

function ChartDateFilterBlock({
  title,
  description,
  draftStart,
  draftEnd,
  onDraftStartChange,
  onDraftEndChange,
  onSearch,
  showLoading,
}: ChartDateFilterBlockProps) {
  return (
    <div className="bg-white rounded-2xl shadow p-4 border border-gray-100 space-y-3">
      <h3 className="text-sm font-semibold text-gray-700">{title}</h3>
      <p className="text-xs text-gray-500">{description}</p>
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-xs text-gray-600">
          시작일
          <input
            type="date"
            value={draftStart}
            onChange={(e) => onDraftStartChange(e.target.value)}
            className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm"
          />
        </label>
        <span className="text-gray-400 pb-1">~</span>
        <label className="flex flex-col gap-1 text-xs text-gray-600">
          종료일
          <input
            type="date"
            value={draftEnd}
            onChange={(e) => onDraftEndChange(e.target.value)}
            className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm"
          />
        </label>
        <button
          type="button"
          onClick={onSearch}
          className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700"
        >
          조회
        </button>
        {showLoading && <span className="text-xs text-gray-500">불러오는 중…</span>}
      </div>
    </div>
  );
}

type FuelDailyChartProps = {
  title: string;
  footnote: string;
  categories: string[];
  volumeData: number[];
  diffData: number[];
  lineColor: string;
  barColor: string;
};

function FuelDailyChart({ title, footnote, categories, volumeData, diffData, lineColor, barColor }: FuelDailyChartProps) {
  const sharedTooltip = { shared: true, intersect: false } as const;

  if (categories.length === 0) {
    return (
      <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
        <h3 className="text-sm font-semibold text-gray-700 mb-1">{title}</h3>
        <p className="text-xs text-gray-500 mb-2">{footnote}</p>
        <p className="text-sm text-gray-500 py-8 text-center">조회 기간을 선택해 주세요.</p>
      </div>
    );
  }

  const series = [
    { name: SERIES_VOLUME, type: 'bar' as const, data: volumeData },
    { name: SERIES_DIFF, type: 'line' as const, data: diffData },
  ];

  return (
    <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
      <h3 className="text-sm font-semibold text-gray-700 mb-1">{title}</h3>
      <p className="text-xs text-gray-500 mb-2">{footnote}</p>
      <ReactApexChart
        type="line"
        height={360}
        series={series}
        options={{
          chart: {
            type: 'line',
            stacked: false,
            toolbar: { show: true },
            zoom: { enabled: true },
          },
          stroke: { width: [0, 3], curve: 'smooth' },
          plotOptions: { bar: { columnWidth: '45%' } },
          colors: [lineColor, barColor],
          xaxis: {
            categories,
            title: { text: '일자' },
            labels: { rotate: -45 },
          },
          yaxis: [
            {
              seriesName: SERIES_VOLUME,
              title: { text: '판매량 (L)' },
              labels: {
                formatter: (v: number) => (Number.isFinite(v) ? Math.round(v).toLocaleString() : ''),
              },
            },
            {
              seriesName: SERIES_DIFF,
              opposite: true,
              title: { text: '판매차 (L)' },
              labels: {
                formatter: (v: number) => (Number.isFinite(v) ? Math.round(v).toLocaleString() : ''),
              },
            },
          ],
          legend: { position: 'top', horizontalAlign: 'right' },
          dataLabels: { enabled: false },
          tooltip: {
            ...sharedTooltip,
            y: {
              formatter: (val: number) => (Number.isFinite(val) ? `${val.toLocaleString(undefined, { maximumFractionDigits: 1 })} L` : ''),
            },
          },
        }}
      />
    </div>
  );
}

type SalesChartsSectionProps = {
  /** 엑셀 업로드 등으로 통계를 다시 불러올 때 증가 */
  refreshTrigger?: number;
};

export default function SalesChartsSection({ refreshTrigger = 0 }: SalesChartsSectionProps) {
  const [stats, setStats] = useState<SalesStatsResponse | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [draftStart, setDraftStart] = useState(defaultStartDate);
  const [draftEnd, setDraftEnd] = useState(defaultEndDate);
  const [appliedRange, setAppliedRange] = useState(() => ({
    start: defaultStartDate(),
    end: defaultEndDate(),
  }));

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      const res = await dashboardApi.getStats({
        startDate: appliedRange.start,
        endDate: appliedRange.end,
      });
      setStats(res.data.data);
    } catch (e) {
      console.error('판매 통계 로딩 실패:', e);
      toast.error('판매 통계를 불러오지 못했습니다.');
      setStats(null);
    } finally {
      setStatsLoading(false);
    }
  }, [appliedRange.start, appliedRange.end]);

  useEffect(() => {
    void loadStats();
  }, [loadStats, refreshTrigger]);

  const onSearch = () => {
    if (!draftStart || !draftEnd) {
      toast.error('시작일과 종료일을 입력해 주세요.');
      return;
    }
    if (draftStart > draftEnd) {
      toast.error('시작일은 종료일보다 이전이어야 합니다.');
      return;
    }
    setAppliedRange({ start: draftStart, end: draftEnd });
  };

  const { categories, gasolineVol, gasolineDiff, dieselVol, dieselDiff } = useMemo(() => {
    const cats = eachDateInRange(appliedRange.start, appliedRange.end);
    const daily = stats?.daily ?? [];
    const dailyDiff = stats?.dailyDiff ?? [];
    const dailyMap = new Map(daily.map((d) => [d.date, d]));
    const diffMap = new Map(dailyDiff.map((d) => [d.date, d]));
    return {
      categories: cats,
      gasolineVol: cats.map((d) => dailyMap.get(d)?.gasoline ?? 0),
      gasolineDiff: cats.map((d) => diffMap.get(d)?.gasoline ?? 0),
      dieselVol: cats.map((d) => dailyMap.get(d)?.diesel ?? 0),
      dieselDiff: cats.map((d) => diffMap.get(d)?.diesel ?? 0),
    };
  }, [stats, appliedRange.start, appliedRange.end]);

  const monthly = stats?.monthly ?? [];
  const weekly = stats?.weekly ?? [];
  const dayOfWeekRows = normalizeDayOfWeek(stats);

  const monthlyCats = monthly.map((m) => m.month);
  const monthlySeries = [
    { name: '휘발유 (L)', data: monthly.map((m) => m.gasoline) },
    { name: '경유 (L)', data: monthly.map((m) => m.diesel) },
  ];

  const weeklyCats = weekly.map((w) => w.weekStart);
  const weeklySeries = [
    { name: '휘발유 (L)', data: weekly.map((w) => w.gasoline) },
    { name: '경유 (L)', data: weekly.map((w) => w.diesel) },
  ];

  const dowSeries = [
    { name: '휘발유 (L)', data: dayOfWeekRows.map((d) => d.gasoline) },
    { name: '경유 (L)', data: dayOfWeekRows.map((d) => d.diesel) },
  ];

  const commonLegend = { position: 'top' as const, horizontalAlign: 'right' as const };
  const sharedTooltip = { shared: true, intersect: false } as const;

  const noStatsBanner =
    !stats && !statsLoading && (
      <div className="bg-amber-50 border border-amber-100 rounded-xl p-3 text-sm text-amber-900 mb-4">
        통계를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.
      </div>
    );

  return (
    <section className="space-y-6">
      <h2 className="text-lg font-bold text-gray-800">판매 통계</h2>
      {noStatsBanner}

      <ChartDateFilterBlock
        title="차트 조회 기간"
        description="일별·월별·주별·요일별 차트와 엑셀 판매차에 동일한 기간이 적용됩니다. 조회 후 아래 모든 차트가 갱신됩니다."
        draftStart={draftStart}
        draftEnd={draftEnd}
        onDraftStartChange={setDraftStart}
        onDraftEndChange={setDraftEnd}
        onSearch={onSearch}
        showLoading={statsLoading}
      />

      <FuelDailyChart
        title="휘발유 일별 현황"
        footnote="좌측: 일별 판매량(bar, tb_transaction) · 우측: 일별 판매차(line, 엑셀 I열·동일 날짜 최신 행)"
        categories={categories}
        volumeData={gasolineVol}
        diffData={gasolineDiff}
        lineColor={COLOR_GASOLINE}
        barColor={FUEL_CHART_COLORS.GASOLINE.dark}
      />

      <FuelDailyChart
        title="경유 일별 현황"
        footnote="좌측: 일별 판매량(bar) · 우측: 일별 판매차(line, 엑셀 P열·동일 날짜 최신 행)"
        categories={categories}
        volumeData={dieselVol}
        diffData={dieselDiff}
        lineColor={COLOR_DIESEL}
        barColor={FUEL_CHART_COLORS.DIESEL.dark}
      />

      <ChartDateFilterBlock
        title="월·주·요일별 차트 조회 기간"
        description="위와 동일한 시작일·종료일을 사용합니다. 월별·주별·요일별 판매량 집계에도 이 기간이 반영됩니다."
        draftStart={draftStart}
        draftEnd={draftEnd}
        onDraftStartChange={setDraftStart}
        onDraftEndChange={setDraftEnd}
        onSearch={onSearch}
        showLoading={statsLoading}
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 items-stretch">
        <div className="bg-white rounded-2xl shadow p-4 border border-gray-100 min-w-0">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">월별 판매량</h3>
          {monthlyCats.length > 0 ? (
            <ReactApexChart
              type="bar"
              height={320}
              series={monthlySeries}
              options={{
                chart: { type: 'bar', stacked: true, toolbar: { show: false } },
                plotOptions: { bar: { horizontal: false, columnWidth: '55%' } },
                xaxis: { categories: monthlyCats },
                yaxis: { title: { text: '판매량 (L)' } },
                colors: [COLOR_GASOLINE, COLOR_DIESEL],
                legend: commonLegend,
                dataLabels: { enabled: false },
                tooltip: sharedTooltip,
              }}
            />
          ) : (
            <p className="text-sm text-gray-500 py-8 text-center">월별 데이터 없음</p>
          )}
        </div>

        <div className="bg-white rounded-2xl shadow p-4 border border-gray-100 min-w-0">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">요일별 평균 판매량</h3>
          <p className="text-xs text-gray-500 mb-2">해당 요일이 포함된 각 날짜의 일 합계를 구한 뒤, 그 일 합계들의 평균 (L)</p>
          <ReactApexChart
            type="bar"
            height={320}
            series={dowSeries}
            options={{
              chart: { type: 'bar', stacked: true, toolbar: { show: false } },
              plotOptions: { bar: { horizontal: false, columnWidth: '55%' } },
              xaxis: { categories: DOW_LABELS },
              yaxis: { title: { text: '평균 판매량 (L)' } },
              colors: [COLOR_GASOLINE, COLOR_DIESEL],
              legend: commonLegend,
              dataLabels: { enabled: false },
              tooltip: sharedTooltip,
            }}
          />
        </div>
      </div>

      <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
        <h3 className="text-sm font-semibold text-gray-700 mb-2">주별 판매량</h3>
        <p className="text-xs text-gray-500 mb-2">주 시작일(월요일) 기준 합계</p>
        {weeklyCats.length > 0 ? (
          <ReactApexChart
            type="bar"
            height={320}
            series={weeklySeries}
            options={{
              chart: { type: 'bar', stacked: true, toolbar: { show: false } },
              plotOptions: { bar: { horizontal: false, columnWidth: '55%' } },
              xaxis: { categories: weeklyCats, labels: { rotate: -35 } },
              yaxis: { title: { text: '판매량 (L)' } },
              colors: [COLOR_GASOLINE, COLOR_DIESEL],
              legend: commonLegend,
              dataLabels: { enabled: false },
              tooltip: sharedTooltip,
            }}
          />
        ) : (
          <p className="text-sm text-gray-500 py-8 text-center">주별 데이터 없음</p>
        )}
      </div>
    </section>
  );
}
