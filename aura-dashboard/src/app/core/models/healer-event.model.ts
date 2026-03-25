export type HealerEventType =
  | 'RECONCILIATION_STARTED'
  | 'RECONCILIATION_COMPLETED'
  | 'RECONCILIATION_FAILED'
  | 'POLICY_APPLIED'
  | 'HEALTH_DEGRADED'
  | 'HEALTH_RECOVERED';

export type HealerSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface HealerEvent {
  eventType: HealerEventType;
  policy: string;
  action: string;
  severity: HealerSeverity;
  timestamp: string;
  serviceName: string;
  namespace: string;
  details?: string;
  aiDiagnosis?: string;
  aiConfidence?: number;
  metrics?: {
    p95ResponseMs?: number;
    openIncidents?: number;
    automationSuccessRatio?: number;
  };
}

export type ClusterNodeHealth = 'HEALTHY' | 'DEGRADED' | 'CRITICAL' | 'UNKNOWN';
export type HealingState = 'STABLE' | 'HEALING' | 'BLOCKED';

export interface ClusterNode {
  serviceName: string;
  namespace: string;
  health: ClusterNodeHealth;
  healingState: HealingState;
  replicas: number;
  healthyReplicas: number;
  lastAction: string;
  lastEventAt: string;
  aiDiagnosis?: string;
  aiConfidence?: number;
}

export interface DashboardKpis {
  openIncidents: number;
  automationSuccess: number;
  p95ResponseMs: number;
  totalEvents: number;
  mttrSeconds: number;
  openIncidentsTrend: number[];
  processedEventsTrend: number[];
}

export interface HealerEventWire {
  event_type: string;
  policy: string;
  action: string;
  severity: string;
  timestamp: string;
  service_name: string;
  namespace: string;
  details?: string;
  ai_diagnosis?: string;
  ai_confidence?: number;
  metrics?: {
    p95_response_ms?: number;
    open_incidents?: number;
    automation_success_ratio?: number;
  };
}
