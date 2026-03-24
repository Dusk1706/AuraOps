# Spec: AuraOps Phase 1 - The Brain (aura-api-analyzer)

**Date:** 2026-03-23
**Status:** Draft (Pending Review)
**Scope:** Root Cause Analysis (RCA) Engine using Spring AI.

## 1. Objective
Build a modular, high-performance microservice capable of analyzing Kubernetes telemetry (logs, metrics, traces) using LLMs to provide deterministic remediation actions without fallback code.

## 2. Architecture: Hexagonal (Ports & Adapters)
The core logic is isolated from frameworks and external providers.

### 2.1 Domain Layer
- **Models:** Uses Java 21 `Records` for immutability and `Sealed Classes` for explicit state management of analysis results (`Success`, `Inconclusive`, `CriticalFailure`).
- **Services:** `IncidentAnalysisService` orchestrates the logic of when and how to analyze an incident.

### 2.2 Application Layer
- **Inbound Ports:** `AnalyzeIncidentUseCase` interface.
- **Outbound Ports:** `LLMProvider` (for AI), `TelemetrySource` (for logs/metrics).

### 2.3 Infrastructure Layer
- **Inbound Adapters:** REST Controller (Spring Boot 3.5) implementing the `API_Contracts.md` schema.
- **Outbound Adapters:** 
    - `SpringAIAdapter`: Implements `LLMProvider` using Spring AI `ChatModel`. Configurable via properties for OpenAI, Anthropic, or Ollama.
    - `LokiAdapter`: (Future) Fetches logs from Grafana Loki.

## 3. The "No-Fallback" Implementation
- **Strict Typing:** Every LLM response is parsed into a `Success` or `Inconclusive` record.
- **Error Handling:** Any infrastructure failure (network, API limits) results in a `CriticalFailure` record with a specific `ErrorCode`. No generic exceptions or nulls are allowed to propagate.
- **Virtual Threads:** All I/O operations (LLM calls, Telemetry fetching) MUST run on Project Loom virtual threads for maximum concurrency.

## 4. Technology Stack
- **Language:** Java 21.
- **Framework:** Spring Boot 3.5.
- **AI Integration:** Spring AI 1.0.
- **Build Tool:** Gradle (Kotlin DSL).
- **Testing:** JUnit 5, Mockito, Testcontainers.

## 5. Success Criteria
- [ ] Successful parsing of logs into a structured `AnalysisResult`.
- [ ] < 90% test coverage in the Domain layer.
- [ ] Swapping LLM providers via `application.yml` without code changes.
- [ ] Validated integration with a mock Kubernetes environment via Testcontainers.
