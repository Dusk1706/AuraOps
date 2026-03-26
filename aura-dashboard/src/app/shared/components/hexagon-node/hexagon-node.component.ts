import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClusterNodeHealth, HealingState } from '../../../core/models/healer-event.model';

@Component({
  selector: 'app-hexagon-node',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="hexagon-container" [ngClass]="containerClass()" title="{{ name() }} - {{ health() }}">
      <svg viewBox="0 0 100 115.47" class="hexagon-svg">
        <polygon points="50 0, 100 28.87, 100 86.6, 50 115.47, 0 86.6, 0 28.87"></polygon>
      </svg>
      <div class="content">
        <div class="label">{{ label() }}</div>
        @if (healingState() === 'HEALING') {
          <div class="healing-indicator pulse"></div>
        }
      </div>
    </div>
  `,
  styles: [`
    .hexagon-container {
      position: relative;
      width: 80px;
      height: 92.38px;
      margin: 4px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: transform 0.2s ease;
    }

    .hexagon-container:hover {
      transform: scale(1.05);
    }

    .hexagon-svg polygon {
      fill: var(--hex-bg, #2a2a2a);
      stroke: var(--hex-border, #444);
      stroke-width: 4;
      transition: fill 0.3s ease, stroke 0.3s ease;
    }

    .content {
      position: absolute;
      text-align: center;
      color: white;
      font-size: 0.75rem;
      font-weight: 500;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    .label {
      max-width: 60px;
      text-overflow: ellipsis;
      overflow: hidden;
      white-space: nowrap;
    }

    .healing-indicator {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: #fbbf24;
      margin-top: 4px;
    }

    .pulse {
      animation: pulse 1.5s infinite;
    }

    @keyframes pulse {
      0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(251, 191, 36, 0.7); }
      70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(251, 191, 36, 0); }
      100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(251, 191, 36, 0); }
    }

    /* States */
    .hex-healthy { --hex-bg: #064e3b; --hex-border: #10b981; }
    .hex-degraded { --hex-bg: #78350f; --hex-border: #f59e0b; }
    .hex-critical { --hex-bg: #7f1d1d; --hex-border: #ef4444; }
    .hex-unknown { --hex-bg: #374151; --hex-border: #6b7280; }
  `]
})
export class HexagonNodeComponent {
  name = signal<string>('');
  health = signal<ClusterNodeHealth>('UNKNOWN');
  healingState = signal<HealingState>('STABLE');

  @Input() set nodeName(value: string) { this.name.set(value); }
  @Input() set nodeHealth(value: ClusterNodeHealth) { this.health.set(value); }
  @Input() set nodeHealingState(value: HealingState) { this.healingState.set(value); }

  label = computed(() => {
    const n = this.name();
    if (!n) return '';
    return n.length > 8 ? n.substring(0, 8) + '...' : n;
  });

  containerClass = computed(() => {
    const h = this.health();
    switch (h) {
      case 'HEALTHY': return 'hex-healthy';
      case 'DEGRADED': return 'hex-degraded';
      case 'CRITICAL': return 'hex-critical';
      default: return 'hex-unknown';
    }
  });
}
