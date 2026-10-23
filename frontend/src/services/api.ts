import axios from 'axios';
import type {
  CommonResponse,
  DashboardResponse,
  SalesStatsResponse,
  Tank,
  Transaction,
  StockIn,
  StockAdjustment,
  StationInfo,
  SellingPrice,
  CompetitorStation,
  PriceSimulateResponse,
  PriceTimelinePoint,
  TankDailyRemainingPoint,
  ExcelUploadBatch,
  ExcelUploadRow,
} from '../types';
import type { FuelType } from '../types';

const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  },
);

export const dashboardApi = {
  get: () => client.get<CommonResponse<DashboardResponse>>('/dashboard'),
  getStats: (params?: { startDate?: string; endDate?: string }) =>
    client.get<CommonResponse<SalesStatsResponse>>('/dashboard/stats', { params }),
};

export const tankApi = {
  getAll: () => client.get<CommonResponse<Tank[]>>('/tanks'),
  getOne: (id: number) => client.get<CommonResponse<Tank>>(`/tanks/${id}`),
  getDailyRemaining: (id: number, params: { from?: string; to?: string }) =>
    client.get<CommonResponse<TankDailyRemainingPoint[]>>(`/tanks/${id}/daily-remaining`, { params }),
  getAttention: () => client.get<CommonResponse<Tank[]>>('/tanks/attention'),
  create: (data: Partial<Tank>) => client.post<CommonResponse<Tank>>('/tanks', data),
  updateSensor: (id: number, data: { temperature?: number; waterLevel?: number }) =>
    client.patch<CommonResponse<Tank>>(`/tanks/${id}/sensor`, data),
  updateInventory: (id: number, data: { maxCapacity: number; currentVolume: number }) =>
    client.patch<CommonResponse<Tank>>(`/tanks/${id}/inventory`, data),
};

// 백엔드가 LocalDateTime ISO 형식 요구: yyyy-MM-ddTHH:mm:ss
export const transactionApi = {
  getRecent: () => client.get<CommonResponse<Transaction[]>>('/transactions/recent'),
  getByPeriod: (from: string, to: string) =>
    client.get<CommonResponse<Transaction[]>>('/transactions', { params: { from, to } }),
  create: (data: {
    tankId: number;
    liter: number;
    unitPrice: number;
    paymentType: string;
    approvalNo?: string;
  }) => client.post<CommonResponse<Transaction>>('/transactions', data),
  uploadExcel: (file: File, incrementalOnly = true, alignOpeningFromExcel = !incrementalOnly) => {
    const form = new FormData();
    form.append('file', file);
    form.append('incrementalOnly', String(incrementalOnly));
    form.append('alignOpeningFromExcel', String(alignOpeningFromExcel));
    return client.post<
      CommonResponse<{
        inserted: number;
        batchId: number;
        transactionInserted: number;
        stockInInserted: number;
        overwritten: number;
        skippedDates: number;
        adjustmentInserted: number;
        adjustmentSkipped: number;
        adjustmentOverwrittenDeletes: number;
      }>
    >('/transactions/upload-excel', form, {
      timeout: 120000,
    });
  },
};

export const excelUploadApi = {
  getBatches: (from: string, to: string) =>
    client.get<CommonResponse<ExcelUploadBatch[]>>('/excel-uploads', {
      params: { from, to },
    }),
  getRows: (batchId: number) =>
    client.get<CommonResponse<ExcelUploadRow[]>>(`/excel-uploads/${batchId}/rows`),
  purgeOperationalData: () => client.delete<CommonResponse<null>>('/excel-uploads/operational-data'),
};

export const stockInApi = {
  getByPeriod: (from: string, to: string) =>
    client.get<CommonResponse<StockIn[]>>('/stock-in', { params: { from, to } }),
  getByTank: (tankId: number) =>
    client.get<CommonResponse<StockIn[]>>(`/stock-in/tank/${tankId}`),
  create: (data: {
    tankId: number;
    inVolume: number;
    buyPrice: number;
    vendorName?: string;
    inDate: string;
    memo?: string;
  }) => client.post<CommonResponse<StockIn>>('/stock-in', data),
};

export const adjustmentApi = {
  getByPeriod: (from?: string, to?: string) =>
    client.get<CommonResponse<StockAdjustment[]>>('/adjustments', from ? { params: { from, to } } : {}),
  getByTank: (tankId: number) =>
    client.get<CommonResponse<StockAdjustment[]>>(`/adjustments/tank/${tankId}`),
  create: (data: {
    tankId: number;
    bookStock: number;
    physicalStock: number;
    adjDate: string;
    memo?: string;
  }) => client.post<CommonResponse<StockAdjustment>>('/adjustments', data),
};

export const authApi = {
  login: (data: { username: string; password: string }) =>
    client.post<CommonResponse<{ token: string }>>('/auth/login', data),
};

export const priceApi = {
  simulate: (data: {
    fuelType: string;
    avgBuyPrice: number;
    targetMarginRate: number;
    competitorAvgPrice?: number;
  }) => client.post<CommonResponse<PriceSimulateResponse>>('/price/simulate', data),
  getHistory: (fuelType: string) => client.get(`/price/history?fuelType=${fuelType}`),
  getTimeline: (params: { fuelType?: 'GASOLINE' | 'DIESEL'; from?: string; to?: string }) =>
    client.get<CommonResponse<PriceTimelinePoint[]>>('/price/timeline', { params }),
};

export const stationInfoApi = {
  get: () => client.get<CommonResponse<StationInfo | null>>('/station-info'),
  update: (data: Partial<StationInfo>) => client.put<CommonResponse<StationInfo>>('/station-info', data),
};

export const sellingPriceApi = {
  getAll: () => client.get<CommonResponse<SellingPrice[]>>('/selling-price'),
  upsert: (data: { fuelType: FuelType; sellingPrice: number; effectiveFrom?: string }) =>
    client.post<CommonResponse<SellingPrice>>('/selling-price', data),
};

export const competitorApi = {
  getAround: (fuelType: FuelType, radius: number) =>
    client.get<CommonResponse<CompetitorStation[]>>('/competitor', { params: { fuelType, radius } }),
};
