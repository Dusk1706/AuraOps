# AuraOps: AI-Driven Kubernetes Self-Healing Platform

![Architecture Concept](https://img.shields.io/badge/Architecture-Event%20Driven-blue) ![Kubernetes](https://img.shields.io/badge/Kubernetes-Native-blue) ![Zero Trust](https://img.shields.io/badge/Security-Zero%20Trust-green)

AuraOps is a senior-level, enterprise-grade Kubernetes ecosystem designed to autonomously monitor, diagnose, and heal microservices. By combining a native Kubernetes Operator with LLM-powered root cause analysis, AuraOps reduces Mean Time To Recovery (MTTR) from minutes to seconds without human intervention.

## 🌟 The Vision & Architecture

AuraOps is designed across 7 strategic phases, culminating in a robust, GitOps-driven, and highly observable platform.

### 1. The Brain: Aura API Analyzer (Spring AI)
A stateless Spring Boot application built with **Spring AI** and **Java 21 Virtual Threads**. It receives telemetry (logs from Loki, metrics from Prometheus, traces from Tempo), passes it to an LLM (OpenAI, Anthropic, or Ollama), and outputs a deterministic root cause diagnosis and a concrete remediation action.

### 2. The Healer: Aura Kubernetes Operator (Java Operator SDK)
A custom controller that watches the cluster for anomalies based on `HealerPolicy` CRDs. When an issue occurs, it collects context, queries the Analyzer, evaluates the suggested action against **Resilience4j** rate limiters and safety policies, and asynchronously executes Kubernetes API commands to heal the deployment (e.g., rolling restarts, scaling).

### 3. The Network: Zero Trust & Service Mesh (Istio & Vault)
A strict **Istio Service Mesh** ensures all inter-service communication is encrypted via mTLS. **HashiCorp Vault** serves as the internal PKI for Istio and uses the Vault Agent Injector to dynamically inject sensitive credentials (like LLM API keys) directly into pod memory, eliminating static Kubernetes Secrets.

### 4. The Dashboard: Real-time Command Center (Angular 19) *(Upcoming)*
A high-fidelity frontend utilizing the **Angular Signals API** for ultra-fast, granular DOM updates. It connects to the backend via **WebSockets (STOMP)** to provide a real-time, live-updating map of cluster health and autonomous actions.

### 5. Infrastructure as Code: GitOps & Terraform *(Upcoming)*
The entire AWS infrastructure (EKS, RDS, MSK) will be codified in **Terraform** using reusable modules, remote S3 state, and DynamoDB locking. The application layer will be packaged using **Helm Charts** for consistent deployments across Dev/Staging/Prod.

### 6. DevSecOps & Quality Assurance *(Upcoming)*
A rigorous CI/CD pipeline built on GitHub Actions featuring:
*   **Pact Contract Testing:** Ensuring backward compatibility between the Operator and the Analyzer.
*   **SonarQube:** Code quality and test coverage enforcement.
*   **Snyk & Trivy:** Continuous vulnerability scanning for Java dependencies, Node.js packages, and Docker images.

### 7. Observability: LGTM Stack *(Upcoming)*
A modern observability stack powered by OpenTelemetry:
*   **Loki:** Log aggregation.
*   **Tempo:** Distributed tracing.
*   **Mimir/Prometheus:** High-cardinality metrics.
*   **Grafana:** Unified dashboarding.

---

## 🚀 Getting Started (Current State: Phase 3 Completed)

Currently, Phase 1 (Analyzer), Phase 2 (Operator), and Phase 3 (Zero Trust Network) are fully implemented.

### Prerequisites
*   Docker & Kubernetes Cluster (e.g., Docker Desktop, K3s, Minikube)
*   `kubectl` & `kustomize`

### Deployment

The backend and infrastructure are packaged as a self-contained Kustomize setup.

```bash
kubectl apply -k deploy
```

This will automatically spin up:
1.  **HashiCorp Vault** (with setup jobs for PKI and secret injection)
2.  **Istio Control Plane** (with strict mTLS policies)
3.  **Kiali** (for mesh visualization)
4.  **AuraOps Operator & Analyzer**

### Project Structure
*   [`/aura-api-analyzer`](./aura-api-analyzer/README.md) - The AI reasoning engine.
*   [`/aura-operator`](./aura-operator/README.md) - The Kubernetes native controller.
*   `/deploy` - Kustomize manifests for local/production deployments.
*   `/infra` - Sub-modules for security (Istio, Vault, Kiali) and observability.
