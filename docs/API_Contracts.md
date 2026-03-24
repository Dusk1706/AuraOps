# AuraOps: API Contracts & Interface Definitions

## 1. AI Analysis Request (Core -> AI)
**Endpoint:** `POST /api/v1/analyze`
**Purpose:** Enviar contexto tecnico para diagnostico.

```json
{
  "incident_id": "uuid-12345",
  "resource": {
    "kind": "Deployment",
    "name": "payment-service",
    "namespace": "prod"
  },
  "telemetry": {
    "logs": ["...log lines..."],
    "metrics": {
      "memory_usage": "1.2Gi",
      "cpu_usage": "800m",
      "restart_count": 5
    },
    "traces": ["trace-id-abc-123"]
  }
}
```

## 2. AI Diagnosis Response (AI -> Core)
**Purpose:** Devolver una accion de remediacion determinista.

```json
{
  "diagnosis": "Memory leak detected in JPA connection pool",
  "confidence": 0.98,
  "recommended_action": {
    "type": "RESTART",
    "parameters": {
      "strategy": "RollingUpdate",
      "capture_heap_dump": true
    }
  },
  "explanation": "Log analysis shows consistent GC overhead and exhaustion of HikariCP pool."
}
```

## 3. Healer Event Schema
**Purpose:** Eventos enviados al Dashboard via WebSockets.

```json
{
  "event_type": "RECONCILIATION_STARTED",
  "policy": "payment-service-heal",
  "action": "RESTART",
  "severity": "CRITICAL",
  "timestamp": "2026-03-23T10:05:00Z"
}
```

## 4. Error Schema (No-Fallback)
Toda respuesta de error debe incluir un codigo especifico para que el Operador decida si escalar a un humano.
La implementacion usa `ProblemDetail` (RFC 7807) y agrega propiedades extendidas para mantener el contrato de AuraOps.
`trace_id` y `context_link` son opcionales. Spring tambien serializa `instance` con la ruta HTTP del request.

```json
{
  "type": "https://auraops.dev/problems/ai_low_confidence",
  "title": "Analysis Inconclusive",
  "status": 422,
  "detail": "AI could not determine root cause with enough certainty (Confidence: 0.65)",
  "instance": "/api/v1/analyze",
  "error_code": "AI_LOW_CONFIDENCE",
  "message": "AI could not determine root cause with enough certainty (Confidence: 0.65)",
  "requires_human": true,
  "trace_id": "0af7651916cd43dd8448eb211c80319c",
  "context_link": "https://grafana.auraops.io/..."
}
```

## 5. Provider Swap
El proveedor del `ChatModel` se selecciona solo por configuracion usando perfiles Spring:

- `openai`
- `anthropic`
- `ollama`

Ejemplo:

```bash
SPRING_PROFILES_ACTIVE=anthropic
```
