import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import {
  DASHBOARD_REALTIME_CONFIG,
  defaultDashboardRealtimeConfig,
} from './core/config/dashboard-realtime.config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    {
      provide: DASHBOARD_REALTIME_CONFIG,
      useValue: defaultDashboardRealtimeConfig,
    },
  ]
};
