import ReactApexChart from 'react-apexcharts';
import type { DashboardTankSummary } from '../../types';
import StatusBadge from '../common/StatusBadge';

interface Props {
  tank: DashboardTankSummary;
  onClick?: () => void;
}

const FUEL_LABEL: Record<string, string> = {
  GASOLINE: '휘발유',
  DIESEL: '경유',
  LPG: 'LPG',
  PREMIUM: '프리미엄',
};

const STATUS_COLOR: Record<string, string> = {
  NORMAL: '#22c55e',
  WARNING: '#f59e0b',
  DANGER: '#ef4444',
};

function fmtIsoDate(iso: string) {
  const [y, m, d] = iso.split('-');
  if (!y || !m || !d) return iso;
  return `${y}.${m}.${d}`;
}

export default function TankGauge({ tank, onClick }: Props) {
  const fillRatio = Number(tank.fillRatio);
  const color = STATUS_COLOR[tank.status];

  const chartOptions: ApexCharts.ApexOptions = {
    chart: { type: 'radialBar', sparkline: { enabled: true } },
    plotOptions: {
      radialBar: {
        startAngle: -135,
        endAngle: 135,
        track: { background: '#e5e7eb', strokeWidth: '100%' },
        dataLabels: {
          name: { show: false },
          value: {
            fontSize: '22px',
            fontWeight: 700,
            color: '#111827',
            formatter: (val) => `${val.toFixed(1)}%`,
          },
        },
      },
    },
    fill: { colors: [color] },
    stroke: { lineCap: 'round' },
  };

  return (
    <div className="flex flex-col">
      <div
        className="bg-white rounded-2xl shadow p-4 cursor-pointer hover:shadow-md transition-shadow border border-gray-100"
        onClick={onClick}
      >
        <div className="flex items-center justify-between mb-1">
          <div>
            <p className="font-bold text-gray-900">{tank.tankName}</p>
            <p className="text-xs text-gray-500">{FUEL_LABEL[tank.fuelType] ?? tank.fuelType}</p>
          </div>
          <StatusBadge status={tank.status} />
        </div>

        <ReactApexChart
          options={chartOptions}
          series={[fillRatio]}
          type="radialBar"
          height={160}
        />

        <div className="mt-2 grid grid-cols-2 gap-1 text-xs text-gray-600">
          <span>잔량</span>
          <span className="text-left font-medium">
            {Number(tank.currentVolume).toLocaleString()}L
          </span>
          <span>최대</span>
          <span className="text-left font-medium">
            {Number(tank.maxCapacity).toLocaleString()}L
          </span>
          {tank.stockRunwayDays != null && (
            <>
              <span>소진 예상(일)</span>
              <span className="text-left font-medium text-blue-600">
                {tank.stockRunwayDays.toFixed(1)}일
              </span>
            </>
          )}
          {tank.temperature != null && (
            <>
              <span>온도</span>
              <span className="text-left font-medium">{tank.temperature}°C</span>
            </>
          )}
        </div>
      </div>
      <p className="mt-1.5 text-xs text-gray-400 text-left">
        마지막판매{' '}
        {tank.lastSaleDate != null ? fmtIsoDate(tank.lastSaleDate) : '—'}
      </p>
    </div>
  );
}
