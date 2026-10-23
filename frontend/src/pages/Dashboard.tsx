import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { dashboardApi } from '../services/api';
import { useWebSocket } from '../hooks/useWebSocket';
import { showTankAlert } from '../utils/tankAlert';
import TankGauge from '../components/tanks/TankGauge';
import ExcelUploadWidget from '../components/dashboard/ExcelUploadWidget';
import SalesChartsSection from '../components/dashboard/SalesChartsSection';
import type { DashboardResponse, StockUpdateMessage } from '../types';

export default function Dashboard() {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [chartRefresh, setChartRefresh] = useState(0);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    try {
      const dashRes = await dashboardApi.get();
      setDashboard(dashRes.data.data);
    } catch (e) {
      console.error('데이터 로딩 실패:', e);
      toast.error('대시보드 데이터를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  const onExcelUploadSuccess = useCallback(() => {
    void fetchData();
    setChartRefresh((c) => c + 1);
  }, [fetchData]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // 실시간 WebSocket: 재고 업데이트 수신 시 대시보드 갱신
  useWebSocket({
    onStockUpdate: useCallback((msg: StockUpdateMessage) => {
      setDashboard((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          tanks: prev.tanks.map((t) =>
            t.tankId === msg.tankId
              ? { ...t, currentVolume: msg.currentVolume, fillRatio: msg.fillRatio, status: msg.status }
              : t,
          ),
        };
      });
    }, []),
    onAlert: showTankAlert,
  });

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }

  if (!dashboard) return null;

  const { today, tanks } = dashboard;

  return (
    <div className="p-6 space-y-6">
      {/* 오늘 요약 카드 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <SummaryCard label="오늘 매출" value={`₩${today.totalRevenue.toLocaleString()}`} color="blue" />
        <SummaryCard label="오늘 판매량" value={`${Number(today.totalSoldLiter).toLocaleString()}L`} color="indigo" />
        <SummaryCard label="예상 마진" value={`₩${today.estimatedMargin.toLocaleString()}`} color="green" />
        <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
          <p className="text-xs text-gray-500 mb-1">탱크 상태</p>
          <div className="flex gap-2 flex-wrap mt-1">
            <span className="text-sm font-bold text-green-600">정상 {today.tankCount - today.warningCount - today.dangerCount}</span>
            <span className="text-sm font-bold text-yellow-600">주의 {today.warningCount}</span>
            <span className="text-sm font-bold text-red-600">위험 {today.dangerCount}</span>
          </div>
        </div>
      </div>

      {/* 탱크 게이지 그리드 */}
      <section>
        <h2 className="text-lg font-bold text-gray-800 mb-3">탱크 현황</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {tanks.map((tank) => (
            <TankGauge
              key={tank.tankId}
              tank={tank}
              onClick={() => navigate(`/tanks/${tank.tankId}`)}
            />
          ))}
        </div>
      </section>

      <ExcelUploadWidget onUploadSuccess={onExcelUploadSuccess} />

      <SalesChartsSection refreshTrigger={chartRefresh} />
    </div>
  );
}

function SummaryCard({ label, value, color }: { label: string; value: string; color: string }) {
  const colorMap: Record<string, string> = {
    blue: 'text-blue-600',
    indigo: 'text-indigo-600',
    green: 'text-green-600',
  };
  return (
    <div className="bg-white rounded-2xl shadow p-4 border border-gray-100">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p className={`text-xl font-bold ${colorMap[color] ?? 'text-gray-900'}`}>{value}</p>
    </div>
  );
}
