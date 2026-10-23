import { useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { StockAlertMessage, StockUpdateMessage } from '../types';

interface UseWebSocketOptions {
  onStockUpdate?: (msg: StockUpdateMessage) => void;
  onAlert?: (msg: StockAlertMessage) => void;
}

export function useWebSocket({ onStockUpdate, onAlert }: UseWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(() => {
    const token = localStorage.getItem('token');
    const stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        // 모든 탱크 재고 업데이트 구독
        stompClient.subscribe('/topic/stock/*', (message) => {
          if (onStockUpdate) {
            const data: StockUpdateMessage = JSON.parse(message.body);
            onStockUpdate(data);
          }
        });
        // 탱크 알림 구독
        stompClient.subscribe('/topic/alert', (message) => {
          if (onAlert) {
            const data: StockAlertMessage = JSON.parse(message.body);
            onAlert(data);
          }
        });
      },
    });

    stompClient.activate();
    clientRef.current = stompClient;
  }, [onStockUpdate, onAlert]);

  useEffect(() => {
    connect();
    return () => {
      clientRef.current?.deactivate();
    };
  }, [connect]);
}
