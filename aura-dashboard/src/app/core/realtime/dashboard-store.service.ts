import { Injectable, computed, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  ClusterNode,
  ClusterNodeHealth,
  DashboardKpis,
  HealerEvent,
  HealerEventWire,
  HealingState,
} from '../models/healer-event.model';
import { firstValueFrom } from 'rxjs';
import { mapWireEvent } from './event-mapper';

@Injectable({ providedIn: 'root' })
export class DashboardStoreService {
  private readonly http = inject(HttpClient);
  private readonly eventsState = signal<HealerEvent[]>([]);
  private readonly nodesState = signal<Map<string, ClusterNode>>(new Map());

  readonly events = computed(() => this.eventsState());

  readonly nodes = computed(() => Array.from(this.nodesState().values()));

  readonly connectionLabel = signal<'CONNECTED' | 'CONNECTING' | 'DISCONNECTED'>('CONNECTING');

  readonly kpis = computed<DashboardKpis>(() => {
    const events = this.eventsState();
    const latestMetric = events.find((event) => event.metrics !== undefined)?.metrics;

    const successfulActions = events.filter(
      (event) => event.eventType === 'RECONCILIATION_COMPLETED'
    ).length;
    const failedActions = events.filter((event) => event.eventType === 'RECONCILIATION_FAILED').length;
    const automatedActions = successfulActions + failedActions;

    const automationSuccess =
      latestMetric?.automationSuccessRatio !== undefined
        ? Math.round(latestMetric.automationSuccessRatio * 100)
        : automatedActions === 0
          ? 100
          : Math.round((successfulActions / automatedActions) * 100);

    const openIncidents =
      latestMetric?.openIncidents ??
      events.filter((event) => event.eventType === 'RECONCILIATION_STARTED').length;

    const p95ResponseMs = latestMetric?.p95ResponseMs ?? 180;
    const mttrSeconds = this.calculateMttrSeconds(events);

    return {
      openIncidents,
      automationSuccess,
      p95ResponseMs,
      totalEvents: events.length,
      mttrSeconds,
      openIncidentsTrend: this.buildTrend(events, 'openIncidents'),
      processedEventsTrend: this.buildTrend(events, 'processedEvents'),
    };
  });

  async init(): Promise<void> {
    try {
      const [nodes, eventsWire] = await Promise.all([
        firstValueFrom(this.http.get<ClusterNode[]>('/api/dashboard/nodes')),
        firstValueFrom(this.http.get<HealerEventWire[]>('/api/dashboard/events')),
      ]);

      this.nodesState.set(new Map(nodes.map((node) => [this.nodeKey(node.serviceName, node.namespace), node])));
      this.eventsState.set(eventsWire.map(mapWireEvent).slice(0, 100));
    } catch (error) {
      console.error('Failed to initialize dashboard state', error);
    }
  }

  ingestEvent(event: HealerEvent): void {
    this.eventsState.update((current) => [event, ...current].slice(0, 100));
    this.nodesState.update((current) => {
      const next = new Map(current);
      const key = this.nodeKey(event.serviceName, event.namespace);
      const existing = next.get(key);

      const replicas = existing?.replicas ?? 2;
      const healthyReplicas = this.nextHealthyReplicas(existing, event);

      next.set(key, {
        serviceName: event.serviceName,
        namespace: event.namespace,
        health: this.healthFromEvent(event),
        healingState: this.healingStateFromEvent(event),
        replicas,
        healthyReplicas,
        lastAction: event.action,
        lastEventAt: event.timestamp,
        aiDiagnosis: event.aiDiagnosis ?? event.details ?? existing?.aiDiagnosis,
        aiConfidence: event.aiConfidence ?? existing?.aiConfidence,
      });

      return next;
    });
  }

  setConnectionLabel(value: 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED'): void {
    this.connectionLabel.set(value);
  }

  private nextHealthyReplicas(existing: ClusterNode | undefined, event: HealerEvent): number {
    if (!existing) {
      return event.severity === 'CRITICAL' ? 1 : 2;
    }

    switch (event.eventType) {
      case 'RECONCILIATION_COMPLETED':
      case 'HEALTH_RECOVERED':
        return existing.replicas;
      case 'RECONCILIATION_FAILED':
      case 'HEALTH_DEGRADED':
        return Math.max(1, existing.replicas - 1);
      default:
        return existing.healthyReplicas;
    }
  }

  private healthFromEvent(event: HealerEvent): ClusterNodeHealth {
    if (event.eventType === 'HEALTH_RECOVERED' || event.eventType === 'RECONCILIATION_COMPLETED') {
      return 'HEALTHY';
    }

    if (event.severity === 'CRITICAL' || event.eventType === 'RECONCILIATION_FAILED') {
      return 'CRITICAL';
    }

    if (event.severity === 'HIGH' || event.eventType === 'HEALTH_DEGRADED') {
      return 'DEGRADED';
    }

    return 'UNKNOWN';
  }

  private healingStateFromEvent(event: HealerEvent): HealingState {
    if (event.eventType === 'RECONCILIATION_STARTED') {
      return 'HEALING';
    }

    if (event.eventType === 'RECONCILIATION_FAILED') {
      return 'BLOCKED';
    }

    return 'STABLE';
  }

  private calculateMttrSeconds(events: HealerEvent[]): number {
    const startsByPolicy = new Map<string, number>();
    const resolvedDurations: number[] = [];

    for (const event of [...events].reverse()) {
      const eventTimeMs = Date.parse(event.timestamp);
      if (Number.isNaN(eventTimeMs)) {
        continue;
      }

      if (event.eventType === 'RECONCILIATION_STARTED') {
        startsByPolicy.set(event.policy, eventTimeMs);
      }

      if (event.eventType === 'RECONCILIATION_COMPLETED') {
        const start = startsByPolicy.get(event.policy);
        if (start !== undefined && eventTimeMs >= start) {
          resolvedDurations.push(Math.round((eventTimeMs - start) / 1000));
          startsByPolicy.delete(event.policy);
        }
      }
    }

    if (resolvedDurations.length === 0) {
      return 0;
    }

    const total = resolvedDurations.reduce((sum, duration) => sum + duration, 0);
    return Math.round(total / resolvedDurations.length);
  }

  private buildTrend(events: HealerEvent[], metric: 'openIncidents' | 'processedEvents'): number[] {
    const maxPoints = 12;
    const timeline = [...events].reverse().slice(-maxPoints);

    if (timeline.length === 0) {
      return Array.from({ length: maxPoints }, () => 0);
    }

    const trend: number[] = [];
    let runningOpen = 0;
    let runningProcessed = 0;

    for (const event of timeline) {
      if (event.eventType === 'RECONCILIATION_STARTED') {
        runningOpen += 1;
      }

      if (event.eventType === 'RECONCILIATION_COMPLETED' || event.eventType === 'RECONCILIATION_FAILED') {
        runningOpen = Math.max(0, runningOpen - 1);
        runningProcessed += 1;
      }

      trend.push(metric === 'openIncidents' ? runningOpen : runningProcessed);
    }

    while (trend.length < maxPoints) {
      trend.unshift(0);
    }

    return trend;
  }

  private nodeKey(serviceName: string, namespace: string): string {
    return `${namespace}/${serviceName}`;
  }
}
