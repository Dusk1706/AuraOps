# AuraOps: Architecture Deep Dive

## 1. The HealerPolicy CRD
El corazón de la reconciliación es el recurso personalizado (CRD).

### Spec (Estado Deseado)
```yaml
apiVersion: auraops.io/v1
kind: HealerPolicy
metadata:
  name: api-gateway-auto-heal
spec:
  targetDeployment: api-gateway
  strategies:
    - type: MemoryLeak
      action: RESTART_WITH_DUMP
      threshold: 85%
    - type: LatencySpike
      action: SCALE_OUT
      maxReplicas: 10
  aiConfidenceThreshold: 0.95
```

### Status (Estado Real/Observado)
```yaml
status:
  lastAction: RESTART_WITH_DUMP
  timestamp: "2026-03-23T10:00:00Z"
  aiDiagnosis: "Detected heap growth pattern in logs (OOM imminent)"
  healthScore: 0.98
```

## 2. Reconciler Workflow (JOSDK)
1.  **Event Trigger:** El Operador detecta un cambio en las métricas de un Pod o una anomalía en los logs vía Loki.
2.  **Context Aggregation:** El Operador recolecta:
    *   Logs (últimos 500 líneas).
    *   Trazas (ID de la traza lenta).
    *   Métricas de recursos (CPU/MEM).
3.  **AI Consultation:** Envía el contexto al `aura-api-analyzer` (Spring AI).
4.  **Decision Making:** Si la confianza es alta, el Operador actualiza el `HealerPolicy.status` y ejecuta la acción sobre el API de Kubernetes.
5.  **Verification:** El Operador espera a que el nuevo Pod pase los `Readiness Probes` antes de marcar la reconciliación como completada.

## 3. Modular Components
*   **aura-core:** Modelos comunes y utilidades de seguridad.
*   **aura-api-analyzer:** El "Cerebro" (Spring AI).
*   **aura-operator:** El "Brazo" (JOSDK).
*   **aura-dashboard:** Interfaz de control (Angular 19).

## 4. Resilience Patterns
*   **Quorum/Rate Limiting:** El Operador nunca reiniciará más del 25% de los pods simultáneamente, incluso si la IA lo sugiere, para evitar caídas totales del servicio.
*   **State Machine:** Cada acción de reparación es una transición de estado persistida, permitiendo recuperarse de fallos del propio AuraOps.
