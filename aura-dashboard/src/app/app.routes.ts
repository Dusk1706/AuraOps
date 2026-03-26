import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./core/layout/main-layout/main-layout.component').then((m) => m.MainLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/overview/pages/overview/overview.component').then((m) => m.OverviewComponent),
      },
      {
        path: 'incident/:serviceName',
        loadComponent: () =>
          import('./features/incidents/pages/incident-detail/incident-detail.component').then(
            (m) => m.IncidentDetailComponent
          ),
      },
      {
        path: 'events',
        loadComponent: () =>
          import('./features/telemetry/pages/logs/logs.component').then((m) => m.LogsComponent),
      },
      {
        path: 'nodes',
        loadComponent: () =>
          import('./features/settings/pages/settings/settings.component').then((m) => m.SettingsComponent),
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
