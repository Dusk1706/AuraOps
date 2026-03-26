import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HexagonNodeComponent } from '../../../../shared/components/hexagon-node/hexagon-node.component';
import { DashboardStoreService } from '../../../../core/realtime/dashboard-store.service';
import { ClusterNode } from '../../../../core/models/healer-event.model';
import { KpiStripComponent } from '../../../dashboard/components/kpi-strip/kpi-strip.component';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule, HexagonNodeComponent, KpiStripComponent, MatCardModule, MatIconModule, RouterModule],
  template: `
    <div class="overview-container">
      <div class="header">
        <h1>Command Center Overview</h1>
      </div>

      <app-kpi-strip [kpis]="store.kpis()"></app-kpi-strip>

      <div class="main-content">
        <div class="hex-grid-section">
          <h2>Cluster Topography</h2>
          @for (group of nodesByNamespace(); track group[0]) {
            <mat-card class="namespace-card">
              <mat-card-header>
                <mat-card-title><mat-icon>folder</mat-icon> Namespace: {{ group[0] }}</mat-card-title>
              </mat-card-header>
              <mat-card-content class="hex-grid">
                @for (node of group[1]; track node.serviceName) {
                  <!-- Instead of standard routing, we could just link to incident detail or similar later. For now, it's a node -->
                  <a [routerLink]="['/incident', node.serviceName]" class="node-link">
                    <app-hexagon-node 
                      [nodeName]="node.serviceName" 
                      [nodeHealth]="node.health" 
                      [nodeHealingState]="node.healingState">
                    </app-hexagon-node>
                  </a>
                }
              </mat-card-content>
            </mat-card>
          }
        </div>

        <div class="activity-feed-section">
          <mat-card>
            <mat-card-header>
              <mat-card-title>Clean Activity Feed</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <ul class="activity-list">
                @for (event of store.events().slice(0, 10); track $index) {
                  <li>
                    <span class="timestamp">{{ event.timestamp | date:'shortTime' }}</span>
                    <span class="severity" [ngClass]="event.severity.toLowerCase()">[{{ event.severity }}]</span>
                    <span class="message">{{ event.serviceName }} ({{ event.namespace }}): {{ event.eventType }} - {{ event.action || event.details }}</span>
                  </li>
                }
                @if (store.events().length === 0) {
                  <li class="empty-state">No recent activity.</li>
                }
              </ul>
            </mat-card-content>
          </mat-card>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .overview-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
      color: white; /* dark mode assumes */
    }
    
    .header h1 {
      margin: 0;
      font-size: 24px;
      font-weight: 300;
    }

    .main-content {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 24px;
    }

    .namespace-card {
      margin-bottom: 24px;
      background: #1e1e2d;
      color: white;
    }

    .namespace-card mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .hex-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      padding-top: 16px;
    }

    .activity-feed-section mat-card {
      background: #1e1e2d;
      color: white;
    }

    .activity-list {
      list-style-type: none;
      padding: 0;
      margin: 0;
      font-size: 0.85rem;
    }

    .activity-list li {
      padding: 8px 0;
      border-bottom: 1px solid #333;
      display: flex;
      gap: 8px;
      align-items: flex-start;
    }

    .activity-list li:last-child {
      border-bottom: none;
    }

    .timestamp {
      color: #94a3b8;
      min-width: 60px;
    }

    .severity.critical { color: #ef4444; }
    .severity.high { color: #f97316; }
    .severity.medium { color: #eab308; }
    .severity.low { color: #3b82f6; }
    .severity.info { color: #8b5cf6; }

    .message {
      color: #e2e8f0;
      word-break: break-word;
    }

    .empty-state {
      color: #64748b;
      font-style: italic;
    }

    /* responsiveness */
    @media (max-width: 1024px) {
      .main-content {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class OverviewComponent {
  store = inject(DashboardStoreService);

  nodesByNamespace = computed(() => {
    const nodes = this.store.nodes();
    const groups = new Map<string, ClusterNode[]>();
    for (const node of nodes) {
      const list = groups.get(node.namespace) || [];
      list.push(node);
      groups.set(node.namespace, list);
    }
    return Array.from(groups.entries());
  });
}
