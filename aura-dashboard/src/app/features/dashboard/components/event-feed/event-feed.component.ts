import { Component, input, inject } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HealerEvent } from '../../../../core/models/healer-event.model';
import { NotificationService } from '../../../../core/services/notification.service';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-event-feed',
  imports: [NgClass, DatePipe, MatCardModule, MatButtonModule, MatIconModule],
  templateUrl: './event-feed.component.html',
  styleUrl: './event-feed.component.scss',
})
export class EventFeedComponent {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(NotificationService);
  readonly events = input.required<HealerEvent[]>();

  eventIcon(eventType: HealerEvent['eventType']): string {
    switch (eventType) {
      case 'RECONCILIATION_STARTED':
      case 'HEALTH_DEGRADED':
        return '[ALERT]';
      case 'POLICY_APPLIED':
        return '[AI]';
      case 'RECONCILIATION_COMPLETED':
      case 'HEALTH_RECOVERED':
        return '[OK]';
      case 'RECONCILIATION_FAILED':
        return '[WARN]';
      default:
        return '[INFO]';
    }
  }

  isPendingApproval(event: HealerEvent): boolean {
    return event.eventType === 'POLICY_APPLIED' && (event.details?.includes('manual approval') ?? false);
  }

  async approveAction(event: HealerEvent): Promise<void> {
    try {
      await firstValueFrom(this.http.post(`/api/approvals/${event.namespace}/${event.policy}/approve`, {}));
      this.notifications.success(`Action ${event.action} approved for ${event.serviceName}.`);
    } catch (error) {
      this.notifications.error('Failed to approve healing action.');
      console.error('Approval failed', error);
    }
  }

  eventNarrative(event: HealerEvent): string {
    if (this.isPendingApproval(event)) {
      return `CRITICAL: Action ${event.action} on ${event.serviceName} requires manual approval before execution.`;
    }

    switch (event.eventType) {
      case 'RECONCILIATION_STARTED':
        return `Analyzer evaluating telemetry for ${event.serviceName}.`;
      case 'POLICY_APPLIED':
        return `Decision made: ${event.action} for policy ${event.policy}.`;
      case 'RECONCILIATION_COMPLETED':
        return `Operator executed ${event.action} successfully on ${event.serviceName}.`;
      case 'RECONCILIATION_FAILED':
        return `Healing flow blocked while applying ${event.action}.`;
      case 'HEALTH_DEGRADED':
        return `${event.serviceName} degraded. Operator preparing mitigation.`;
      case 'HEALTH_RECOVERED':
        return `${event.serviceName} recovered and returned to healthy state.`;
      default:
        return event.details ?? 'New cluster event received.';
    }
  }

  severityClass(severity: HealerEvent['severity']): string {
    switch (severity) {
      case 'CRITICAL':
        return 'critical';
      case 'HIGH':
        return 'high';
      case 'MEDIUM':
        return 'medium';
      default:
        return 'low';
    }
  }
}
