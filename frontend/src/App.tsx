import { BrowserRouter, Routes, Route, NavLink, Navigate, Outlet, Link, useNavigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import StockInPage from './pages/StockInPage';
import SalesPage from './pages/SalesPage';
import TankDetailPage from './pages/TankDetailPage';
import AdjustmentPage from './pages/AdjustmentPage';
import LoginPage from './pages/LoginPage';
import PriceSimulatorPage from './pages/PriceSimulatorPage';
import MyInfoPage from './pages/MyInfoPage';
import ExcelUploadLogPage from './pages/ExcelUploadLogPage';

const NAV_ITEMS = [
  { to: '/',               label: '대시보드' },
  { to: '/stock-in',       label: '입고 관리' },
  { to: '/sales',          label: '판매 관리' },
  { to: '/adjustments',    label: '재고 조정' },
  { to: '/excel-uploads',  label: '업로드 이력' },
  { to: '/my-info',        label: '정보 관리' },
];

function ProtectedLayout() {
  const token = localStorage.getItem('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <AppLayout />;
}

function AppLayout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <header className="bg-white shadow-sm border-b border-gray-200 px-6 py-3 flex items-center gap-6 justify-between flex-wrap">
        <div className="flex items-center gap-6 flex-wrap min-w-0">
          <Link
            to="/"
            className="flex items-center gap-2 shrink-0 rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            title="대시보드로 이동"
          >
            <span className="text-xl font-extrabold text-blue-700 tracking-tight">FuelSync Pro</span>
          </Link>
          <nav className="flex gap-1 flex-wrap">
            {NAV_ITEMS.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-blue-100 text-blue-700'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          className="shrink-0 px-4 py-2 rounded-lg text-sm font-medium text-gray-700 border border-gray-200 hover:bg-gray-100 transition-colors outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
        >
          로그아웃
        </button>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 로그인은 헤더 없이 독립 렌더링 */}
        <Route path="/login" element={<LoginPage />} />

        {/* 인증 필요 — 헤더 포함 레이아웃 */}
        <Route element={<ProtectedLayout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/stock-in" element={<StockInPage />} />
          <Route path="/sales" element={<SalesPage />} />
          <Route path="/tanks/:tankId" element={<TankDetailPage />} />
          <Route path="/adjustments" element={<AdjustmentPage />} />
          <Route path="/price" element={<PriceSimulatorPage />} />
          <Route path="/my-info" element={<MyInfoPage />} />
          <Route path="/excel-uploads" element={<ExcelUploadLogPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
