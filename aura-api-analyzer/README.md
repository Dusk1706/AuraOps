# Aura API Analyzer

The Analyzer is the AI-driven "brain" of the AuraOps ecosystem. It is a stateless Spring Boot application built with Spring AI.

## Core Responsibilities

1.  **Context Processing:** Receives structured incident context (logs, metrics, and traces) from the Aura Operator.
2.  **LLM Integration:** Interfaces with an LLM (OpenAI, Anthropic, or Ollama) to analyze the provided telemetry.
3.  **Deterministic Decision Making:** Returns a strictly formatted JSON response detailing the diagnosis, confidence score, and recommended remediation action (e.g., `ROLLING_RESTART`, `SCALE_OUT`).
4.  **Graceful Degradation:** Emits an `INCONCLUSIVE` result if the required telemetry is missing or if the AI confidence is below the established threshold, preventing destructive guesswork.

## Security

This service does not read API keys from environment variables or Kubernetes Secrets. It relies on the HashiCorp Vault Agent Injector to mount a transient `application.properties` file containing the necessary credentials at runtime.
