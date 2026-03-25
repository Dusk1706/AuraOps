import { Component, input } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { ClusterNode } from '../../../../core/models/healer-event.model';

@Component({
  selector: 'app-cluster-map',
  imports: [NgClass, DatePipe, MatCardModule, MatChipsModule, MatIconModule],
  templateUrl: './cluster-map.component.html',
  styleUrl: './cluster-map.component.scss',
})
export class ClusterMapComponent {
  readonly nodes = input.required<ClusterNode[]>();

  healthClass(health: ClusterNode['health']): string {
    switch (health) {
      case 'HEALTHY':
        return 'healthy';
      case 'DEGRADED':
        return 'degraded';
      case 'CRITICAL':
        return 'critical';
      default:
        return 'unknown';
    }
  }

  healingClass(state: ClusterNode['healingState']): string {
    switch (state) {
      case 'HEALING':
        return 'healing';
      case 'BLOCKED':
        return 'blocked';
      default:
        return 'stable';
    }
  }

  confidenceLabel(confidence: number | undefined): string {
    if (confidence === undefined) {
      return 'N/A';
    }

    return `${Math.round(confidence * 100)}%`;
  }
}
