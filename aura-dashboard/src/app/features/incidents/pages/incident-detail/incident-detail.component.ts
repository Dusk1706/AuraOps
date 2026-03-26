import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DashboardStoreService } from '../../../../core/realtime/dashboard-store.service';
import { DependencyGraphComponent } from '../../components/dependency-graph/dependency-graph.component';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

@Component({
  selector: 'app-incident-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, DependencyGraphComponent, MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="incident-detail-container" *ngIf="targetNode() as node; else notFound">
      <div class="header">
        <button mat-icon-button routerLink="/">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <h1>Incident Details: {{ node.serviceName }}</h1>
        <div class="badges">
          <span class="badge state" [ngClass]="node.healingState.toLowerCase()">{{ node.healingState }}</span>
          <span class="badge severity" [ngClass]="node.health.toLowerCase()">Health: {{ node.health }}</span>
        </div>
      </div>

      <div class="content-grid">
        <div class="main-column">
          <mat-card class="graph-card">
            <mat-card-header>
              <mat-card-title>Impact & Dependency Graph</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <app-dependency-graph [targetService]="node.serviceName"></app-dependency-graph>
            </mat-card-content>
          </mat-card>

          <mat-card class="logs-card">
            <mat-card-header>
              <mat-card-title>Recent Events for {{ node.serviceName }}</mat-card-title>
            </mat-card-header>
            <mat-card-content>
               <ul class="activity-list">
                @for (event of recentEvents(); track $index) {
                  <li>
                    <span class="timestamp">{{ event.timestamp | date:'mediumTime' }}</span>
                    <span class="event-type">[{{ event.eventType }}]</span>
                    <span class="message">{{ event.action || event.details }}</span>
                  </li>
                }
              </ul>
            </mat-card-content>
          </mat-card>
        </div>

        <div class="side-column">
          <mat-card class="ai-card">
            <mat-card-header>
              <mat-card-title><mat-icon>psychology</mat-icon> AI Diagnostics</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="confidence-gauge">
                <span>Confidence: {{ (node.aiConfidence || 0) * 100 }}%</span>
                <mat-progress-bar mode="determinate" [value]="(node.aiConfidence || 0) * 100"></mat-progress-bar>
              </div>

              <div class="reasoning">
                <h3>Diagnosis</h3>
                <p>{{ node.aiDiagnosis || 'No active AI diagnosis available for this node.' }}</p>
                
                <h3>Suggested Actions</h3>
                <ul>
                  <li>Examine recent deployment logs</li>
                  <li>Verify external dependencies availability</li>
                  <li>Consider increasing replica scale if load is high</li>
                </ul>
              </div>
            </mat-card-content>
            <mat-card-actions class="actions">
              <button mat-raised-button color="accent" (click)="acknowledge()"><mat-icon>done</mat-icon> Acknowledge</button>
              <button mat-raised-button color="warn" (click)="rollback()"><mat-icon>history</mat-icon> Rollback</button>
            </mat-card-actions>
          </mat-card>
        </div>
      </div>
    </div>
    
    <ng-template #notFound>
      <div class="not-found">
        <h2>Service not found or missing from store.</h2>
        <button mat-button routerLink="/">Return to Dashboard</button>
      </div>
    </ng-template>
  `,
  styles: [`
    .incident-detail-container {
      padding: 24px;
      color: white;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .header {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .header h1 {
      margin: 0;
      flex-grow: 1;
    }

    .badges {
      display: flex;
      gap: 8px;
    }

    .badge {
      padding: 4px 12px;
      border-radius: 16px;
      font-size: 12px;
      font-weight: bold;
      text-transform: uppercase;
    }

    .badge.healing { background: #d97706; color: white; }
    .badge.stable { background: #059669; color: white; }
    .badge.blocked { background: #dc2626; color: white; }

    .badge.critical { background: #991b1b; color: white; }
    .badge.degraded { background: #b45309; color: white; }
    .badge.healthy { background: #065f46; color: white; }
    .badge.unknown { background: #334155; color: white; }

    .content-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 24px;
    }

    mat-card {
      background: #1e1e2d;
      color: white;
      margin-bottom: 24px;
    }

    .confidence-gauge {
      margin: 16px 0;
    }

    .confidence-gauge span {
      display: block;
      margin-bottom: 8px;
      font-weight: 500;
    }

    .reasoning h3 {
      font-size: 14px;
      color: #94a3b8;
      margin-top: 16px;
      margin-bottom: 8px;
    }

    .reasoning p {
      font-size: 14px;
      color: #e2e8f0;
      line-height: 1.5;
    }

    .reasoning ul {
      padding-left: 20px;
      color: #e2e8f0;
      font-size: 14px;
    }

    .actions {
      display: flex;
      gap: 12px;
      padding: 16px;
      border-top: 1px solid #334155;
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

    .timestamp {
      color: #94a3b8;
      min-width: 80px;
    }
    
    .event-type {
      color: #3b82f6;
    }

    @media (max-width: 1024px) {
      .content-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class IncidentDetailComponent {
  private route = inject(ActivatedRoute);
  private store = inject(DashboardStoreService);

  targetNode = computed(() => {
    const serviceName = this.route.snapshot.paramMap.get('serviceName');
    const nodes = this.store.nodes();
    return nodes.find((n: any) => n.serviceName === serviceName);
  });

  recentEvents = computed(() => {
    const serviceName = this.route.snapshot.paramMap.get('serviceName');
    return this.store.events().filter((e: any) => e.serviceName === serviceName).slice(0, 10);
  });

  acknowledge() {
    alert('Incident Acknowledged. Automatic healing paused.');
  }

  rollback() {
    const node = this.targetNode();
    if (confirm(`Are you sure you want to rollback ${node?.serviceName}?`)) {
      alert(`Rollback initiated for ${node?.serviceName}`);
    }
  }
}
