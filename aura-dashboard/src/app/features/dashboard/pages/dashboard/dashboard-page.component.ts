import { Component, inject } from '@angular/core';
import { NgClass } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { ClusterMapComponent } from '../../components/cluster-map/cluster-map.component';
import { EventFeedComponent } from '../../components/event-feed/event-feed.component';
import { KpiStripComponent } from '../../components/kpi-strip/kpi-strip.component';
import { DashboardRealtimeService } from '../../../../core/realtime/dashboard-realtime.service';
import { DashboardStoreService } from '../../../../core/realtime/dashboard-store.service';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    NgClass,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    KpiStripComponent,
    ClusterMapComponent,
    EventFeedComponent,
  ],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
})
export class DashboardPageComponent {
  private readonly realtime = inject(DashboardRealtimeService);
  readonly store = inject(DashboardStoreService);

  constructor() {
    this.realtime.start();
  }

  refreshRealtime(): void {
    this.realtime.reconnect();
  }

  isStale(): boolean {
    return this.store.connectionLabel() !== 'CONNECTED';
  }

  showStaleBanner(): boolean {
    return this.store.connectionLabel() === 'DISCONNECTED';
  }

  connectionClass(): string {
    const status = this.store.connectionLabel();
    if (status === 'CONNECTED') {
      return 'connected';
    }

    if (status === 'CONNECTING') {
      return 'connecting';
    }

    return 'disconnected';
  }
}
