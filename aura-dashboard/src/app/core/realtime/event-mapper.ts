import {
  HealerEvent,
  HealerEventType,
  HealerEventWire,
  HealerSeverity,
} from '../models/healer-event.model';

const allowedEventTypes: ReadonlySet<HealerEventType> = new Set([
  'RECONCILIATION_STARTED',
  'RECONCILIATION_COMPLETED',
  'RECONCILIATION_FAILED',
  'POLICY_APPLIED',
  'HEALTH_DEGRADED',
  'HEALTH_RECOVERED',
]);

const allowedSeverities: ReadonlySet<HealerSeverity> = new Set([
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]);

const parseEventType = (value: string): HealerEventType => {
  if (!allowedEventTypes.has(value as HealerEventType)) {
    throw new Error(`Unsupported event_type: ${value}`);
  }
  return value as HealerEventType;
};

const parseSeverity = (value: string): HealerSeverity => {
  if (!allowedSeverities.has(value as HealerSeverity)) {
    throw new Error(`Unsupported severity: ${value}`);
  }
  return value as HealerSeverity;
};

export const mapWireEvent = (wire: HealerEventWire): HealerEvent => ({
  eventType: parseEventType(wire.event_type),
  policy: wire.policy,
  action: wire.action,
  severity: parseSeverity(wire.severity),
  timestamp: wire.timestamp,
  serviceName: wire.service_name,
  namespace: wire.namespace,
  details: wire.details,
  aiDiagnosis: wire.ai_diagnosis,
  aiConfidence: wire.ai_confidence,
  metrics: wire.metrics
    ? {
        p95ResponseMs: wire.metrics.p95_response_ms,
        openIncidents: wire.metrics.open_incidents,
        automationSuccessRatio: wire.metrics.automation_success_ratio,
      }
    : undefined,
});
