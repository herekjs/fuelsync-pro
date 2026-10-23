import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PriceTimelineSection from '../components/price/PriceTimelineSection';
import { competitorApi, sellingPriceApi, stationInfoApi, tankApi } from '../services/api';
import type { CompetitorStation, FuelType, SellingPrice, StationInfo, Tank } from '../types';

type TabKey = 'price' | 'station' | 'competitor';
type PriceManagedFuel = 'GASOLINE' | 'DIESEL';

const TAB_ITEMS: Array<{ key: TabKey; label: string }> = [
  { key: 'station', label: '주유소 정보' },
  { key: 'price', label: '판매가 관리' },
  { key: 'competitor', label: '경쟁사 판매가' },
];

const FUEL_LABEL: Record<PriceManagedFuel, string> = {
  GASOLINE: '휘발유',
  DIESEL: '경유',
};

const FUEL_TYPES: PriceManagedFuel[] = ['GASOLINE', 'DIESEL'];
const COMPETITOR_FUEL_TYPES: PriceManagedFuel[] = ['GASOLINE', 'DIESEL'];

type CompetitorRow = CompetitorStation & {
  fuelType: FuelType;
};

type GroupedCompetitorRow = {
  stationKey: string;
  stationId: string;
  stationName: string;
  brandName: string;
  address: string;
  distance: number | null;
  gasolinePrice: number | null;
  dieselPrice: number | null;
};

type CompetitorCompareState = 'higher' | 'lower' | 'same' | 'unknown';

export default function MyInfoPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabKey>('station');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const [tanks, setTanks] = useState<Tank[]>([]);
  const [tankDrafts, setTankDrafts] = useState<
    Record<number, { maxCapacity: string; currentVolume: string }>
  >({});
  const [savingTankId, setSavingTankId] = useState<number | null>(null);
  const [sellingPrices, setSellingPrices] = useState<Record<PriceManagedFuel, string>>({
    GASOLINE: '',
    DIESEL: '',
  });
  const [stationInfo, setStationInfo] = useState<Partial<StationInfo>>({
    stationName: '',
    address: '',
    katechX: null,
    katechY: null,
    phone: '',
    brand: '',
  });

  const [radius, setRadius] = useState(10);
  const [competitors, setCompetitors] = useState<CompetitorRow[]>([]);

  const competitorAverageByFuel = useMemo(() => {
    return COMPETITOR_FUEL_TYPES.reduce<Record<PriceManagedFuel, number | null>>((acc, fuel) => {
      const validPrices = competitors
        .filter((row) => row.fuelType === fuel)
        .map((row) => row.price)
        .filter((price): price is number => price != null);

      acc[fuel] = validPrices.length > 0
        ? Math.round(validPrices.reduce((sum, price) => sum + price, 0) / validPrices.length)
        : null;
      return acc;
    }, {
      GASOLINE: null,
      DIESEL: null,
    });
  }, [competitors]);

  const groupedCompetitors = useMemo(() => {
    const groupedMap = new Map<string, GroupedCompetitorRow>();

    competitors.forEach((row) => {
      const stationKey = row.stationId || `${row.stationName}-${row.address}`;
      const current = groupedMap.get(stationKey);

      if (!current) {
        groupedMap.set(stationKey, {
          stationKey,
          stationId: row.stationId,
          stationName: row.stationName,
          brandName: row.brandName,
          address: row.address,
          distance: row.distance,
          gasolinePrice: row.fuelType === 'GASOLINE' ? row.price : null,
          dieselPrice: row.fuelType === 'DIESEL' ? row.price : null,
        });
        return;
      }

      if (row.distance != null && (current.distance == null || row.distance < current.distance)) {
        current.distance = row.distance;
      }

      if (!current.stationName && row.stationName) current.stationName = row.stationName;
      if (!current.brandName && row.brandName) current.brandName = row.brandName;
      if (!current.address && row.address) current.address = row.address;

      if (row.fuelType === 'GASOLINE') current.gasolinePrice = row.price;
      if (row.fuelType === 'DIESEL') current.dieselPrice = row.price;
    });

    return Array.from(groupedMap.values()).sort((a, b) => {
      const aDistance = a.distance ?? Number.POSITIVE_INFINITY;
      const bDistance = b.distance ?? Number.POSITIVE_INFINITY;
      return aDistance - bDistance;
    });
  }, [competitors]);

  const getCompareState = (competitorPrice: number | null, fuelType: 'GASOLINE' | 'DIESEL'): CompetitorCompareState => {
    if (competitorPrice == null) return 'unknown';
    const myPrice = Number(sellingPrices[fuelType]);
    if (!Number.isFinite(myPrice) || myPrice <= 0) return 'unknown';
    if (competitorPrice > myPrice) return 'higher';
    if (competitorPrice < myPrice) return 'lower';
    return 'same';
  };

  const getCompareTextClass = (state: CompetitorCompareState) => {
    if (state === 'higher') return 'text-green-700 font-semibold';
    if (state === 'lower') return 'text-red-700 font-semibold';
    if (state === 'same') return 'text-gray-700 font-semibold';
    return 'text-gray-700';
  };

  useEffect(() => {
    const fetchInitial = async () => {
      setLoading(true);
      setError('');
      try {
        const [tankRes, priceRes, stationRes] = await Promise.all([
          tankApi.getAll(),
          sellingPriceApi.getAll(),
          stationInfoApi.get(),
        ]);

        setTanks(tankRes.data.data);

        const priceMap: Record<PriceManagedFuel, string> = {
          GASOLINE: '',
          DIESEL: '',
        };
        priceRes.data.data.forEach((item: SellingPrice) => {
          if (item.fuelType === 'GASOLINE' || item.fuelType === 'DIESEL') {
            priceMap[item.fuelType] = String(item.sellingPrice);
          }
        });
        setSellingPrices(priceMap);

        const station = stationRes.data.data;
        if (station) {
          setStationInfo({
            stationId: station.stationId,
            stationName: station.stationName ?? '',
            address: station.address ?? '',
            katechX: station.katechX ?? null,
            katechY: station.katechY ?? null,
            phone: station.phone ?? '',
            brand: station.brand ?? '',
          });
        }
      } catch {
        setError('정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };
    fetchInitial();
  }, []);

  const saveSellingPrice = async (type: PriceManagedFuel) => {
    const value = Number(sellingPrices[type]);
    if (!value || value <= 0) {
      setError(`${FUEL_LABEL[type]} 판매가를 올바르게 입력해 주세요.`);
      return;
    }

    setSaving(true);
    setError('');
    setMessage('');
    try {
      await sellingPriceApi.upsert({
        fuelType: type,
        sellingPrice: value,
        effectiveFrom: new Date().toISOString().slice(0, 10),
      });
      setMessage(`${FUEL_LABEL[type]} 판매가를 저장했습니다.`);
    } catch {
      setError('판매가 저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const saveStationInfo = async () => {
    setSaving(true);
    setError('');
    setMessage('');
    try {
      await stationInfoApi.update({
        stationName: stationInfo.stationName ?? '',
        address: stationInfo.address ?? '',
        katechX: stationInfo.katechX ?? null,
        katechY: stationInfo.katechY ?? null,
        phone: stationInfo.phone ?? '',
        brand: stationInfo.brand ?? '',
      });
      setMessage('주유소 정보를 저장했습니다.');
    } catch {
      setError('주유소 정보 저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const saveTankInventory = async (tankId: number) => {
    const draft = tankDrafts[tankId];
    const tank = tanks.find((t) => t.tankId === tankId);
    if (!tank) return;
    const maxStr = draft?.maxCapacity ?? String(tank.maxCapacity);
    const volStr = draft?.currentVolume ?? String(tank.currentVolume);
    const maxCapacity = Number(maxStr);
    const currentVolume = Number(volStr);
    if (!Number.isFinite(maxCapacity) || !Number.isFinite(currentVolume)) {
      setError('최대 용량·현재 재고를 숫자로 입력해 주세요.');
      return;
    }

    setSavingTankId(tankId);
    setError('');
    setMessage('');
    try {
      const res = await tankApi.updateInventory(tankId, { maxCapacity, currentVolume });
      const updated = res.data.data;
      setTanks((prev) => prev.map((t) => (t.tankId === tankId ? updated : t)));
      setTankDrafts((prev) => ({
        ...prev,
        [tankId]: {
          maxCapacity: String(updated.maxCapacity),
          currentVolume: String(updated.currentVolume),
        },
      }));
      setMessage('탱크 재고를 반영했습니다.');
    } catch {
      setError('탱크 재고 저장에 실패했습니다. 값을 확인해 주세요.');
    } finally {
      setSavingTankId(null);
    }
  };

  const fetchCompetitors = async () => {
    setSaving(true);
    setError('');
    setMessage('');
    try {
      const [gasolineRes, dieselRes] = await Promise.all(
        COMPETITOR_FUEL_TYPES.map((type) => competitorApi.getAround(type, radius)),
      );

      const mergedRows: CompetitorRow[] = [
        ...gasolineRes.data.data.map((row) => ({ ...row, fuelType: 'GASOLINE' as const })),
        ...dieselRes.data.data.map((row) => ({ ...row, fuelType: 'DIESEL' as const })),
      ].sort((a, b) => {
        const aDistance = a.distance ?? Number.POSITIVE_INFINITY;
        const bDistance = b.distance ?? Number.POSITIVE_INFINITY;
        return aDistance - bDistance;
      });

      setCompetitors(mergedRows);
      if (mergedRows.length === 0) {
        setMessage('조건에 맞는 경쟁사 판매가가 없습니다.');
      }
    } catch {
      setError('경쟁사 판매가 조회에 실패했습니다. 주유소 위치를 먼저 확인해 주세요.');
    } finally {
      setSaving(false);
    }
  };

  const openKakaoMap = (stationName: string, address: string) => {
    const keyword = [stationName, address].filter((value) => value && value.trim().length > 0).join(' ').trim();
    if (!keyword) return;
    const kakaoMapUrl = `https://map.kakao.com/link/search/${encodeURIComponent(keyword)}`;
    window.open(kakaoMapUrl, '_blank', 'noopener,noreferrer');
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">정보 관리</h1>
        <p className="text-sm text-gray-500 mt-1">주유소·탱크 현황, 판매가, 경쟁사 판매가를 한 화면에서 관리합니다.</p>
      </div>

      <div className="bg-white rounded-2xl shadow border border-gray-100 p-2 inline-flex gap-1">
        {TAB_ITEMS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={`px-4 py-2 rounded-xl text-sm font-medium ${
              tab === item.key ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">{error}</div>}
      {message && <div className="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-xl text-sm">{message}</div>}

      {tab === 'price' && (
        <div className="space-y-4">
          <section className="bg-white rounded-2xl shadow border border-gray-100 p-6 space-y-4">
            <h2 className="text-lg font-bold text-gray-800">유종별 판매가 입력</h2>
            {FUEL_TYPES.map((type) => (
              <div key={type} className="grid grid-cols-1 md:grid-cols-[180px_1fr_120px] gap-3 items-end">
                <div>
                  <label className="label">{FUEL_LABEL[type]}</label>
                  <div className="text-sm text-gray-500">{type}</div>
                </div>
                <div>
                  <label className="label">판매가 (원/L)</label>
                  <input
                    className="input"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={sellingPrices[type]}
                    onChange={(e) => setSellingPrices((prev) => ({ ...prev, [type]: e.target.value }))}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => saveSellingPrice(type)}
                  disabled={saving}
                  className="h-10 bg-blue-600 text-white rounded-xl text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
                >
                  저장
                </button>
              </div>
            ))}
          </section>
          <PriceTimelineSection />
        </div>
      )}

      {tab === 'station' && (
        <div className="space-y-6">
          <section className="bg-white rounded-2xl shadow border border-gray-100 p-6 space-y-4">
            <h2 className="text-lg font-bold text-gray-800">주유소 위치/기본 정보</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="label">주유소명</label>
                <input
                  className="input"
                  value={stationInfo.stationName ?? ''}
                  onChange={(e) => setStationInfo((prev) => ({ ...prev, stationName: e.target.value }))}
                />
              </div>
              <div>
                <label className="label">브랜드</label>
                <input
                  className="input"
                  value={stationInfo.brand ?? ''}
                  onChange={(e) => setStationInfo((prev) => ({ ...prev, brand: e.target.value }))}
                />
              </div>
              <div className="md:col-span-2">
                <label className="label">주소</label>
                <input
                  className="input"
                  value={stationInfo.address ?? ''}
                  onChange={(e) => setStationInfo((prev) => ({ ...prev, address: e.target.value }))}
                />
              </div>
              <div>
                <label className="label">KATECH X (Easting)</label>
                <input
                  className="input"
                  type="number"
                  step="1"
                  value={stationInfo.katechX ?? ''}
                  onChange={(e) =>
                    setStationInfo((prev) => ({
                      ...prev,
                      katechX: e.target.value === '' ? null : Number(e.target.value),
                    }))
                  }
                />
              </div>
              <div>
                <label className="label">KATECH Y (Northing)</label>
                <input
                  className="input"
                  type="number"
                  step="1"
                  value={stationInfo.katechY ?? ''}
                  onChange={(e) =>
                    setStationInfo((prev) => ({
                      ...prev,
                      katechY: e.target.value === '' ? null : Number(e.target.value),
                    }))
                  }
                />
              </div>
              <div>
                <label className="label">연락처</label>
                <input
                  className="input"
                  value={stationInfo.phone ?? ''}
                  onChange={(e) => setStationInfo((prev) => ({ ...prev, phone: e.target.value }))}
                />
              </div>
            </div>
            <button
              type="button"
              onClick={saveStationInfo}
              disabled={saving}
              className="bg-blue-600 text-white rounded-xl px-5 py-2.5 text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
            >
              정보 저장
            </button>
          </section>

          <section className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="text-lg font-bold text-gray-800">탱크 현황</h2>
            </div>
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-600">
                <tr>
                  <th className="px-4 py-3 text-left font-medium">탱크명</th>
                  <th className="px-4 py-3 text-left font-medium">유종</th>
                  <th className="px-4 py-3 text-left font-medium">최대 용량(L)</th>
                  <th className="px-4 py-3 text-left font-medium">현재 재고(L)</th>
                  <th className="px-4 py-3 text-left font-medium">재고율</th>
                  <th className="px-4 py-3 text-left font-medium">상태</th>
                  <th className="px-4 py-3 text-left font-medium w-28"> </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {tanks.map((tank) => {
                  const draft = tankDrafts[tank.tankId];
                  const maxInput = draft?.maxCapacity ?? String(tank.maxCapacity);
                  const volInput = draft?.currentVolume ?? String(tank.currentVolume);
                  const ratioPct = Math.round(Number(tank.fillRatio));
                  const barColor =
                    tank.status === 'NORMAL'
                      ? 'bg-green-500'
                      : tank.status === 'WARNING'
                        ? 'bg-yellow-400'
                        : 'bg-red-500';
                  return (
                    <tr key={tank.tankId} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <button
                          type="button"
                          className="font-medium text-gray-800 text-left hover:text-blue-600 hover:underline"
                          onClick={() => navigate(`/tanks/${tank.tankId}`)}
                        >
                          {tank.tankName}
                        </button>
                      </td>
                      <td className="px-4 py-3">{FUEL_LABEL[tank.fuelType as PriceManagedFuel] ?? tank.fuelType}</td>
                      <td className="px-4 py-3">
                        <input
                          className="input max-w-[140px] text-right py-1.5"
                          type="number"
                          min={0.01}
                          step={1}
                          value={maxInput}
                          onChange={(e) =>
                            setTankDrafts((prev) => ({
                              ...prev,
                              [tank.tankId]: {
                                maxCapacity: e.target.value,
                                currentVolume: volInput,
                              },
                            }))
                          }
                        />
                      </td>
                      <td className="px-4 py-3">
                        <input
                          className="input max-w-[140px] text-right py-1.5"
                          type="number"
                          min={0}
                          step={1}
                          value={volInput}
                          onChange={(e) =>
                            setTankDrafts((prev) => ({
                              ...prev,
                              [tank.tankId]: {
                                maxCapacity: maxInput,
                                currentVolume: e.target.value,
                              },
                            }))
                          }
                        />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-gray-200 rounded-full h-2 min-w-[60px]">
                            <div
                              className={`h-2 rounded-full ${barColor}`}
                              style={{ width: `${Math.min(100, ratioPct)}%` }}
                            />
                          </div>
                          <span className="text-xs text-gray-600 w-10 text-right">{ratioPct}%</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex px-2 py-1 text-xs rounded-full font-semibold ${
                            tank.status === 'NORMAL'
                              ? 'bg-green-100 text-green-700'
                              : tank.status === 'WARNING'
                                ? 'bg-yellow-100 text-yellow-700'
                                : 'bg-red-100 text-red-700'
                          }`}
                        >
                          {tank.status}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <button
                          type="button"
                          disabled={savingTankId !== null}
                          onClick={() => void saveTankInventory(tank.tankId)}
                          className="text-xs font-semibold bg-slate-700 text-white rounded-lg px-3 py-1.5 hover:bg-slate-800 disabled:opacity-50"
                        >
                          {savingTankId === tank.tankId ? '저장…' : '저장'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </section>
        </div>
      )}

      {tab === 'competitor' && (
        <section className="space-y-4">
          <div className="bg-white rounded-2xl shadow border border-gray-100 p-6">
            <h2 className="text-lg font-bold text-gray-800 mb-4">반경 내 경쟁사 판매가 조회</h2>
            <div className="grid grid-cols-1 md:grid-cols-[1fr_140px] gap-3 items-end">
              <div>
                <label className="label">반경 (km)</label>
                <select className="input" value={radius} onChange={(e) => setRadius(Number(e.target.value))}>
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((r) => (
                    <option key={r} value={r}>{r}km</option>
                  ))}
                </select>
              </div>
              <button
                type="button"
                onClick={fetchCompetitors}
                disabled={saving}
                className="h-10 bg-indigo-600 text-white rounded-xl text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50"
              >
                조회
              </button>
            </div>
          </div>

          {(competitorAverageByFuel.GASOLINE != null || competitorAverageByFuel.DIESEL != null) && (
            <div className="bg-indigo-50 border border-indigo-200 rounded-xl p-4">
              <p className="text-sm text-indigo-700">
                경쟁사 평균 판매가:
                {' '}
                휘발유
                {' '}
                <span className={`font-bold ${getCompareTextClass(getCompareState(competitorAverageByFuel.GASOLINE, 'GASOLINE'))}`}>
                  {competitorAverageByFuel.GASOLINE != null ? `₩${competitorAverageByFuel.GASOLINE.toLocaleString()}` : '-'}
                </span>
                {' / '}
                경유
                {' '}
                <span className={`font-bold ${getCompareTextClass(getCompareState(competitorAverageByFuel.DIESEL, 'DIESEL'))}`}>
                  {competitorAverageByFuel.DIESEL != null ? `₩${competitorAverageByFuel.DIESEL.toLocaleString()}` : '-'}
                </span>
                {' / L'}
              </p>
              <p className="mt-2 text-xs text-gray-600">
                <span className="text-green-700 font-semibold">초록</span>
                {' '}
                = 경쟁사가 더 비쌈(내 가격이 저렴)
                {' · '}
                <span className="text-red-700 font-semibold">빨강</span>
                {' '}
                = 경쟁사가 더 쌈(내 가격이 비쌈)
                {' · '}
                <span className="text-gray-700 font-semibold">회색</span>
                {' '}
                = 동일가/비교불가
              </p>
            </div>
          )}

          <div className="bg-white rounded-2xl shadow border border-gray-100 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-600">
                <tr>
                  <th className="px-4 py-3 text-left font-medium">상호</th>
                  <th className="px-4 py-3 text-left font-medium">브랜드</th>
                  <th className="px-4 py-3 text-left font-medium">휘발유(원/L)</th>
                  <th className="px-4 py-3 text-left font-medium">경유(원/L)</th>
                  <th className="px-4 py-3 text-left font-medium">거리(km)</th>
                  <th className="px-4 py-3 text-left font-medium">주소</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {groupedCompetitors.length === 0 ? (
                  <tr>
                    <td className="px-4 py-8 text-center text-gray-400" colSpan={6}>
                      조회 결과가 없습니다.
                    </td>
                  </tr>
                ) : (
                  groupedCompetitors.map((row) => (
                    <tr
                      key={row.stationKey}
                      className="hover:bg-gray-50 cursor-pointer"
                      onClick={() => openKakaoMap(`광주광역시 ${row.stationName}`, row.address)}
                    >
                      <td className="px-4 py-3 font-medium text-blue-700">{row.stationName || '-'}</td>
                      <td className="px-4 py-3">{row.brandName}</td>
                      <td className={`px-4 py-3 ${getCompareTextClass(getCompareState(row.gasolinePrice, 'GASOLINE'))}`}>
                        {row.gasolinePrice != null ? `₩${Number(row.gasolinePrice).toLocaleString()}` : '-'}
                      </td>
                      <td className={`px-4 py-3 ${getCompareTextClass(getCompareState(row.dieselPrice, 'DIESEL'))}`}>
                        {row.dieselPrice != null ? `₩${Number(row.dieselPrice).toLocaleString()}` : '-'}
                      </td>
                      <td className="px-4 py-3">{row.distance != null ? Number(row.distance).toFixed(2) : '-'}</td>
                      <td className="px-4 py-3 text-gray-600">{row.address || '-'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
