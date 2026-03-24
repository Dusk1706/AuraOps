# Aura API Analyzer

The Analyzer acts as the AI reasoning engine of the AuraOps platform. It is a highly concurrent Spring Boot service that processes complex telemetry data to diagnose system failures.

## Architectural Role in AuraOps

In the overarching AuraOps ecosystem (as outlined in the root README), the Analyzer is **Phase 1: The Brain**. It sits behind the Istio Service Mesh and receives HTTP requests exclusively from the Aura Operator.

### Key Capabilities
*   **High-Throughput Processing:** Built on **Java 21 Virtual Threads**, allowing a single instance to handle thousands of concurrent analysis requests without blocking OS threads.
*   **Agnostic AI Integration:** Utilizes **Spring AI** to seamlessly switch between local inference (Ollama) for privacy/cost-efficiency, or cloud providers (OpenAI, Anthropic) for complex reasoning.
*   **Deterministic Fallbacks:** The domain logic forces an `INCONCLUSIVE` result if the telemetry (logs, metrics, or traces) is insufficient, ensuring the AI never guesses blindly.

## Zero Trust Security

As part of Phase 3, this service does not contain static secrets. 
*   **No Environment Variables:** `OPENAI_API_KEY` is not present in the Deployment YAML.
*   **Vault Injection:** Upon pod startup, the HashiCorp Vault Agent Injector authenticates via the pod's `ServiceAccount` and renders the credentials into `/vault/secrets/application.properties`, which Spring Boot imports automatically via `SPRING_CONFIG_IMPORT`.

## Development

```bash
# Run the test suite
./gradlew test

# Build the Docker image
docker build -t auraops/aura-api-analyzer:latest .
```
