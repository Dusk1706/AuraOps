import { InjectionToken } from '@angular/core';

export interface DashboardRealtimeConfig {
  wsEndpoint: string;
  topic: string;
  reconnectDelayMs: number;
}

export const DASHBOARD_REALTIME_CONFIG = new InjectionToken<DashboardRealtimeConfig>(
  'DASHBOARD_REALTIME_CONFIG'
);

export const defaultDashboardRealtimeConfig: DashboardRealtimeConfig = {
  wsEndpoint: '/ws-native',
  topic: '/topic/healer-events',
  reconnectDelayMs: 1500,
};
