export type FuelType = 'GASOLINE' | 'DIESEL' | 'LPG' | 'PREMIUM';
export type TankStatus = 'NORMAL' | 'WARNING' | 'DANGER';
export type PaymentType = 'CARD' | 'CASH' | 'APP';
export type TransactionInputType = 'WEB' | 'EXCEL';
export type AdjustmentInputType = 'WEB' | 'EXCEL';

export interface Tank {
  tankId: number;
  tankName: string;
  fuelType: FuelType;
  maxCapacity: number;
  currentVolume: number;
  fillRatio: number;
  temperature: number | null;
  waterLevel: number | null;
  thresholdWarning: number;
  thresholdDanger: number;
  status: TankStatus;
  updatedAt: string;
}

export interface Transaction {
  txId: number;
  tankId: number;
  tankName: string;
  fuelType: FuelType;
  liter: number;
  unitPrice: number;
  totalAmount: number;
  /** null: 결제수단 미입력(예: 엑셀 업로드) */
  paymentType: PaymentType | null;
  /** WEB: 화면 등록, EXCEL: 엑셀 업로드 */
  inputType: TransactionInputType;
  approvalNo: string | null;
  txDatetime: string;
}

export interface StockIn {
  inId: number;
  tankId: number;
  tankName: string;
  fuelType: FuelType;
  inVolume: number;
  buyPrice: number;
  totalCost: number;
  vendorName: string | null;
  inDate: string;
  memo: string | null;
  createdAt: string;
}

/** 엑셀 업로드 배치 (파일 1개당 1건) */
export interface ExcelUploadBatch {
  batchId: number;
  fileName: string;
  totalRows: number;
  insertedCount: number;
  /** 판매·입고가 INSERT된 서로 다른 일자 수 */
  appliedDateCount: number;
  uploadedAt: string;
}

/** 엑셀에서 파싱된 원본 행 (A·C·D·I·J·K·P·G·H·N·O·W·X·Y·Z) */
export interface ExcelUploadRow {
  rowId: number;
  rowNo: number;
  saleDate: string;
  gasolineInLiter: number | null;
  gasolineSaleLiter: number | null;
  gasolineDiffLiter: number | null;
  gasolineSellingPrice: number | null;
  gasolineBuyPrice: number | null;
  dieselInLiter: number | null;
  dieselSaleLiter: number | null;
  dieselDiffLiter: number | null;
  dieselSellingPrice: number | null;
  dieselBuyPrice: number | null;
  /** W~Z: 재고조정 스냅샷 (배포 이전 저장 행은 응답에 없을 수 있음) */
  adjGasolinePhysicalLiter?: number | null;
  adjGasolineDiffLiter?: number | null;
  adjDieselPhysicalLiter?: number | null;
  adjDieselDiffLiter?: number | null;
  /** 업로드 시 계산 마감(L): W·Y 또는 행시작+입고−판매 */
  gasolineClosingLiter?: number | null;
  dieselClosingLiter?: number | null;
  /** 행 시작 잔량 − 마감 (전일 대비 의미와 동일) */
  gasolineDayAdjLiter?: number | null;
  dieselDayAdjLiter?: number | null;
  /** 이번 업로드에서 DB에 저장된 C·D·J·K (구 배치는 undefined) */
  gasStockInInserted?: boolean;
  gasSaleInserted?: boolean;
  dieselStockInInserted?: boolean;
  dieselSaleInserted?: boolean;
}

export interface DashboardTankSummary extends Tank {
  stockRunwayHours: number | null;
  stockRunwayDays: number | null;
  /** 소진예상(일) 기준 = 최근 입고일 (YYYY-MM-DD) */
  stockRunwayBaselineDate: string | null;
  /** 기준일 이후 데이터 중 마지막 판매일 */
  lastSaleDate: string | null;
  todaySoldLiter: number;
  todayRevenue: number;
}

export interface TodaySummary {
  totalRevenue: number;
  totalSoldLiter: number;
  estimatedMargin: number;
  tankCount: number;
  warningCount: number;
  dangerCount: number;
}

export interface FuelTypeSummary {
  fuelType: FuelType;
  totalVolume: number;
  totalCapacity: number;
  fillRatio: number;
  tankCount: number;
}

export interface DashboardResponse {
  tanks: DashboardTankSummary[];
  today: TodaySummary;
  fuelTypeSummaries: FuelTypeSummary[];
}

export interface SalesStatsMonthly {
  month: string;
  gasoline: number;
  diesel: number;
}

export interface SalesStatsDaily {
  date: string;
  gasoline: number;
  diesel: number;
}

/** 엑셀 I·P열 판매차(L), 날짜별 최신 업로드 행 기준 */
export interface SalesStatsDailyDiff {
  date: string;
  gasoline: number;
  diesel: number;
}

/** weekStart: 해당 주의 월요일 (yyyy-MM-dd) */
export interface SalesStatsWeekly {
  weekStart: string;
  gasoline: number;
  diesel: number;
}

/** dayOfWeek: MariaDB WEEKDAY — 0=월요일 … 6=일요일 */
export interface SalesStatsDayOfWeek {
  dayOfWeek: number;
  gasoline: number;
  diesel: number;
}

export interface SalesStatsResponse {
  monthly: SalesStatsMonthly[];
  weekly: SalesStatsWeekly[];
  daily: SalesStatsDaily[];
  /** 없으면(구 API) 프론트에서 0으로 채움 */
  dayOfWeek?: SalesStatsDayOfWeek[];
  /** 엑셀 판매차 일별; 없으면 빈 배열 */
  dailyDiff?: SalesStatsDailyDiff[];
}

export interface CommonResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errorCode: string | null;
  timestamp: string;
}

export interface StockAdjustment {
  adjId: number;
  tankId: number;
  tankName: string;
  fuelType: FuelType;
  bookStock: number;
  physicalStock: number;
  diffVolume: number;
  diffAmount: number;
  adjDate: string;
  memo: string | null;
  /** WEB: 화면 등록, EXCEL: 엑셀 업로드 */
  inputType: AdjustmentInputType;
  createdAt: string;
}

export interface StationInfo {
  stationId: number;
  stationName: string;
  address: string | null;
  katechX: number | null;
  katechY: number | null;
  phone: string | null;
  brand: string | null;
  updatedAt: string | null;
}

export interface SellingPrice {
  priceId: number;
  fuelType: FuelType;
  sellingPrice: number;
  effectiveFrom: string;
  createdAt: string;
}

export interface CompetitorStation {
  stationId: string;
  stationName: string;
  brand: string;
  brandName: string;
  price: number | null;
  distance: number | null;
  address: string;
}

export interface PriceSimulateResponse {
  fuelType: string;
  avgBuyPrice: number;
  targetMarginRate: number;
  suggestedPrice: number;
  competitorAvgPrice: number | null;
  priceDiffFromCompetitor: number | null;
}

export interface PriceTimelinePoint {
  date: string;
  fuelType: 'GASOLINE' | 'DIESEL';
  avgBuyPrice: number | null;
  avgActualSellingPrice: number | null;
}

/** 일자별 1행 (휘발유·경유 피벗) */
export interface DailyPivotRow {
  date: string;
  gasBuyPrice: number | null;
  gasSellingPrice: number | null;
  gasDiff: number | null;
  dieBuyPrice: number | null;
  dieselSellingPrice: number | null;
  dieDiff: number | null;
}

// WebSocket 메시지 타입
export interface StockUpdateMessage {
  tankId: number;
  tankName: string;
  fuelType: FuelType;
  currentVolume: number;
  maxCapacity: number;
  fillRatio: number;
  status: TankStatus;
}

export interface TankDailyRemainingPoint {
  date: string;
  inVolume: number;
  soldVolume: number;
  adjustmentVolume: number;
  endVolume: number;
}
