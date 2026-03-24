# Aura Kubernetes Operator

The Aura Operator is the execution engine of the self-healing platform. Built with the **Java Operator SDK**, it runs natively within the cluster, watching for anomalies and executing remediation strategies.

## Architectural Role in AuraOps

In the AuraOps ecosystem, this component represents **Phase 2: The Healer**. It acts as the bridge between raw cluster state, observability backends, and the AI Analyzer.

### The Reconciliation Loop
1.  **Monitor:** Watches deployments targeted by a `HealerPolicy` Custom Resource.
2.  **Collect:** When an issue is detected, it queries the cluster's observability stack (**Loki** for logs, **Tempo** for traces) via REST clients.
3.  **Consult:** Sends the aggregated context to the `aura-api-analyzer` via the Istio service mesh.
4.  **Validate:** Passes the AI's recommendation through **Resilience4j** Rate Limiters and predefined Safety Policies to prevent destructive actions (like scaling down a database).
5.  **Execute & Verify:** Applies the change to Kubernetes and transitions to an asynchronous `VERIFYING` state. It uses `InformerEventSource` to watch `ReplicaSet` rollouts, only marking the incident as `HEALED` once the new pods are fully ready.

## Testing Strategy

To ensure absolute reliability, the Operator leverages a multi-tiered testing approach:
*   **Unit Tests:** Business logic (rate limiting, policy evaluation) is tested in isolation.
*   **Integration Tests:** Uses `kubernetes-server-mock` to test the reconciler logic without a real cluster.
*   **End-to-End (E2E) Tests:** Employs **Testcontainers (`K3sContainer`)** to spin up an ephemeral, real K3s cluster. It applies the CRDs, triggers a failure, and asserts that the Operator successfully heals the cluster, ensuring 100% portability across CI/CD pipelines.

## Development

```bash
# Run the test suite (requires Docker for Testcontainers)
./gradlew test

# Build the Docker image
docker build -t auraops/aura-operator:latest .
```
