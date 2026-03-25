import { Component, input } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { HealerEvent } from '../../../../core/models/healer-event.model';

@Component({
  selector: 'app-event-feed',
  imports: [NgClass, DatePipe, MatCardModule],
  templateUrl: './event-feed.component.html',
  styleUrl: './event-feed.component.scss',
})
export class EventFeedComponent {
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

  eventNarrative(event: HealerEvent): string {
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
