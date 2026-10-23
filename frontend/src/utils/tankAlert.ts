import { toast } from 'sonner';
import type { StockAlertMessage } from '../types';

/**
 * 탱크 주의·위험 알림을 토스트로 보여 준다.
 * 같은 탱크의 알림은 id 가 같아 새 토스트가 쌓이지 않고 기존 토스트를 갱신한다.
 */
export function showTankAlert(msg: StockAlertMessage) {
  const text = `${msg.tankName} 잔량 ${msg.currentVolume.toLocaleString()}L (${msg.fillRatio}%)`;
  const options = { id: `tank-alert-${msg.tankId}` };
  if (msg.status === 'DANGER') {
    toast.error(`위험: ${text}`, options);
  } else if (msg.status === 'WARNING') {
    toast.warning(`주의: ${text}`, options);
  }
}
