# Aura Operator

The Operator is the execution engine of the AuraOps ecosystem. It is built using the Java Operator SDK.

## Core Responsibilities

1.  **Reconciliation:** Monitors the state of target deployments via the `HealerPolicy` Custom Resource Definition (CRD).
2.  **Telemetry Collection:** Gathers real-time logs from Loki and distributed traces from Tempo when an anomaly is detected.
3.  **Decision Orchestration:** Packages the telemetry and queries the `aura-api-analyzer` for a remediation plan.
4.  **Safety & Rate Limiting:** Validates the AI's proposed action against predefined safety policies and applies rate limiting (via Resilience4j) to prevent alert storms or cascading failures.
5.  **Execution & Verification:** Executes the validated action against the Kubernetes API. Crucially, it utilizes an asynchronous, non-blocking verification phase (`VERIFYING`) using `InformerEventSource` to monitor `ReplicaSet` generations and ensure rollouts complete successfully before marking an incident as `HEALED`.

## Testing

The Operator features a robust, portable testing suite:
*   **Unit & Integration:** Mocks the Kubernetes API to validate reconciler logic.
*   **End-to-End (E2E):** Uses Testcontainers (`K3sContainer`) to spin up an ephemeral K3s cluster, applying manifests and validating the operator's behavior in a real, isolated Kubernetes environment without depending on the host's native Docker CLI.
