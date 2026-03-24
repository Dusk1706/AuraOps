# AuraOps

AuraOps is an advanced, AI-driven Kubernetes Operator designed for autonomous issue resolution and self-healing. It consists of two primary components communicating securely over an Istio Service Mesh, with dynamic secret management provided by HashiCorp Vault.

## Architecture

*   **aura-api-analyzer (The Brain):** A Spring Boot service leveraging Spring AI. It receives telemetry context (logs, metrics, traces), consults an LLM, and outputs a deterministic root cause diagnosis and a suggested remediation action.
*   **aura-operator (The Healer):** A Java-based Kubernetes Operator (built with Java Operator SDK) that monitors cluster resources. When an anomaly is detected, it collects telemetry via Loki and Tempo, requests a decision from the Analyzer, validates the decision against safety policies, and applies the healing action (e.g., rolling restart, scaling).
*   **Zero Trust Network:** Both components run within an Istio Service Mesh enforcing strict mTLS.
*   **Dynamic Secrets:** Credentials (like OpenAI/Anthropic API keys) are never stored as Kubernetes Secrets. They are injected directly into the pod's memory at runtime using HashiCorp Vault Agent Injector.

## Deployment

The entire system is packaged as a unified, self-contained Kustomize deployment.

```bash
kubectl apply -k deploy
```

This command deploys:
1.  **Vault:** Development server with a setup job to configure PKI and inject initial dummy secrets.
2.  **Istio:** Base and Istiod control plane, plus strict PeerAuthentication policies.
3.  **Kiali:** For network observability and traffic visualization.
4.  **AuraOps:** The Operator and the API Analyzer components.

## Development

The project uses Gradle.

To run the full test suite for the Analyzer:
```bash
cd aura-api-analyzer
./gradlew test
```

To run the full test suite for the Operator (including Testcontainers-based E2E tests):
```bash
cd aura-operator
./gradlew test
```
