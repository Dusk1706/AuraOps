# AuraOps Command Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a 4-screen DevSecOps dashboard for AuraOps using Angular 19, Signals, and a high-fidelity enterprise aesthetic.

**Architecture:** Refactor the existing dashboard into a structured multi-page layout with a persistent sidebar. Use Signal-based state management for real-time updates and a hybrid visualization approach (Hexagon Grid for Overview, Dependency Graph for Diagnostics).

**Tech Stack:** Angular 19, TailwindCSS, RxJS/WebSockets, Lucide Icons (or similar).

---

### Task 1: App Shell & Sidebar Layout

**Files:**
- Create: `aura-dashboard/src/app/core/layout/main-layout/main-layout.component.ts`
- Create: `aura-dashboard/src/app/core/layout/main-layout/main-layout.component.html`
- Modify: `aura-dashboard/src/app/app.routes.ts`
- Modify: `aura-dashboard/src/app/app.component.html`

- [ ] **Step 1: Create MainLayout component with Sidebar and TopBar**
- [ ] **Step 2: Update routes to use MainLayout as a wrapper**
- [ ] **Step 3: Implement responsive Sidebar (Collapsible)**
- [ ] **Step 4: Verify navigation works between placeholder routes**
- [ ] **Step 5: Commit**

### Task 2: Screen 1 – Overview & Hexagon Grid

**Files:**
- Create: `aura-dashboard/src/app/features/overview/pages/overview/overview.component.ts`
- Create: `aura-dashboard/src/app/shared/components/hexagon-node/hexagon-node.component.ts`
- Modify: `aura-dashboard/src/app/core/realtime/dashboard-store.service.ts`

- [ ] **Step 1: Implement SVG HexagonNode component with Signal-based states (Healthy, Healing, Critical)**
- [ ] **Step 2: Create Overview page with KPI Strip and Hexagon Grid grouped by Namespace**
- [ ] **Step 3: Connect Hexagon Grid to `DashboardStoreService.nodes()` signal**
- [ ] **Step 4: Add "Clean Activity Feed" (one-line summaries)**
- [ ] **Step 5: Commit**

### Task 3: Screen 2 – Incidents & AI Diagnostics

**Files:**
- Create: `aura-dashboard/src/app/features/incidents/pages/incident-detail/incident-detail.component.ts`
- Create: `aura-dashboard/src/app/features/incidents/components/dependency-graph/dependency-graph.component.ts`

- [ ] **Step 1: Implement Dependency Graph (Approach A: SVG-based with basic link logic)**
- [ ] **Step 2: Create AI Diagnostic Card with confidence gauge and reasoning list**
- [ ] **Step 3: Implement Manual Action buttons (Acknowledge, Rollback)**
- [ ] **Step 4: Verify deep-linking from Screen 1 works**
- [ ] **Step 5: Commit**

### Task 4: Screen 3 – Live Telemetry (The Engine Room)

**Files:**
- Create: `aura-dashboard/src/app/features/telemetry/pages/logs/logs.component.ts`
- Modify: `aura-dashboard/src/app/core/realtime/dashboard-realtime.service.ts`

- [ ] **Step 1: Create high-performance Log Console with Monospace font**
- [ ] **Step 2: Implement Filters (Severity, Namespace, Pod) and Live Tail toggle**
- [ ] **Step 3: Add expandable JSON blocks with syntax highlighting**
- [ ] **Step 4: Commit**

### Task 5: Screen 4 & RBAC Security UX

**Files:**
- Create: `aura-dashboard/src/app/core/services/rbac.service.ts`
- Create: `aura-dashboard/src/app/features/settings/pages/settings/settings.component.ts`

- [ ] **Step 1: Implement RBAC Signal-based service (`isAdmin()`, `isReadOnly()`)**
- [ ] **Step 2: Create Settings view for Vault/Istio/HealerPolicy status**
- [ ] **Step 3: Add "Read-Only" UI dimming and padlock icons globally**
- [ ] **Step 4: Final verification of responsiveness and performance**
- [ ] **Step 5: Commit**
