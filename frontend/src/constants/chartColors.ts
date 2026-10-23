/** 유종별 차트 색 - 휘발유는 노란 톤, 경유는 파란 톤 */
export const FUEL_CHART_COLORS = {
  GASOLINE: { light: '#facc15', dark: '#ca8a04' },
  DIESEL: { light: '#3b82f6', dark: '#1d4ed8' },
} as const;

export const COLOR_GASOLINE = FUEL_CHART_COLORS.GASOLINE.light;
export const COLOR_DIESEL = FUEL_CHART_COLORS.DIESEL.light;
