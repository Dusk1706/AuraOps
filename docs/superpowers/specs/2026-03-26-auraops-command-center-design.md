# Design Spec: AuraOps Command Center (Angular 19)

**Date:** 2026-03-26  
**Status:** Approved  
**Tech Stack:** Angular 19, Signals, TailwindCSS/Vanilla CSS, RxJS/WebSockets.

---

## 1. Vision & UX Strategy
The AuraOps Command Center is a high-performance DevSecOps dashboard for AI-driven Kubernetes self-healing. It replaces cognitive overload with a structured 4-screen flow, prioritizing "Nominal Status" clarity and deep-dive technical root-cause analysis.

### Core Visual Principles
- **Enterprise-Grade:** Sophisticated Dark Mode (Vercel/Linear style).
- **Semantic Colors:** Green (Healthy/Stable), Amber (Healing/Warning), Red (Critical/Blocked).
- **Typography:** Sans-serif (Inter/Roboto) for UI; Monospace (JetBrains Mono) for logs/JSON.
- **Responsiveness:** Fluid grid for Hexagons; Sidebar-to-BottomNav transition for mobile.

---

## 2. Global Architecture (App Shell)
- **Sidebar (Nav):** Persistent vertical icons (Overview, Incidents, Telemetry, Settings).
- **Top Bar:** Blurred background with "Connectivity Pill" (Signal-driven), Global Search (Cmd+K), and RBAC Role indicator.
- **RBAC Handling:** Global "Read-Only" mode dims interactive elements and adds padlock icons for users without write permissions.

---

## 3. Screen 1: Overview / Global Dashboard
- **Top KPIs:** 5 minimal cards with sparklines (Open Incidents, Success %, p95, MTTR, Events).
- **Hexagon Grid (Namespace Grouped):**
    - **Healthy:** Static outline, subtle breathing animation.
    - **Healing:** Pulsing Amber.
    - **Critical/Blocked:** Flashing Red (2Hz).
- **Clean Activity Feed:** 1-line human-readable summaries only (No JSON).

---

## 4. Screen 2: Incidents & AI Diagnostics
- **Dependency Graph:** Topological view of the blast radius (Parent/Child nodes).
- **AI Diagnostic Card:** 
    - Heuristic checklist (Checks performed vs Findings).
    - Confidence Gauge (%).
    - Operator Intent (e.g., "Scaling replicas").
- **Manual Actions:** Acknowledge, Force Rollback, Approve.

---

## 5. Screen 3: Live Telemetry & System Logs
- **Console UI:** High-performance monospace log stream.
- **Controls:** Live Tail toggle (Play/Pause), Severity/Namespace/Pod filters.
- **Expandable Payloads:** Syntax-highlighted JSON/Stack-trace blocks.

---

## 6. Screen 4: Settings & Policies
- **Integration Cards:** HashiCorp Vault, Istio Service Mesh.
- **HealerPolicy Manager:** List/Toggle for CRDs with reconciliation timestamps.

---

## 7. Implementation Plan (High-Level)
1. **Phase 1:** Refactor `AppShell` with Sidebar and Signal-based TopBar.
2. **Phase 2:** Implement `HexagonGrid` SVG component using Angular 19 `@for` and Signals.
3. **Phase 3:** Develop `IncidentGraph` using a lightweight SVG/Library approach (Approach A).
4. **Phase 4:** Create the `TelemetryConsole` with virtual scrolling for high-volume logs.
5. **Phase 5:** Implement RBAC "Dimming" logic and Settings view.
