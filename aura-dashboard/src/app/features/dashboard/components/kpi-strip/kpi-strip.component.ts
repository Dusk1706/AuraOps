import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { DashboardKpis } from '../../../../core/models/healer-event.model';

@Component({
  selector: 'app-kpi-strip',
  imports: [MatCardModule, MatProgressBarModule],
  templateUrl: './kpi-strip.component.html',
  styleUrl: './kpi-strip.component.scss',
})
export class KpiStripComponent {
  readonly kpis = input.required<DashboardKpis>();

  progressForP95(value: number): number {
    const bounded = Math.max(80, Math.min(value, 400));
    return 100 - Math.round(((bounded - 80) / 320) * 100);
  }

  sparklinePath(points: number[]): string {
    const width = 220;
    const height = 40;
    const safePoints = points.length === 0 ? [0] : points;
    const maxValue = Math.max(...safePoints, 1);
    const stepX = safePoints.length === 1 ? width : width / (safePoints.length - 1);

    return safePoints
      .map((value, index) => {
        const x = Math.round(index * stepX);
        const y = Math.round(height - (value / maxValue) * (height - 4) - 2);
        return `${index === 0 ? 'M' : 'L'} ${x} ${y}`;
      })
      .join(' ');
  }

  mttrDisplay(seconds: number): string {
    if (seconds <= 0) {
      return 'N/A';
    }

    if (seconds < 60) {
      return `${seconds}s`;
    }

    const minutes = Math.floor(seconds / 60);
    const remainder = seconds % 60;
    return `${minutes}m ${remainder}s`;
  }
}
