# AuraOps: Engineering & Quality Guidelines

## 1. Core Philosophy: "No-Fallback Architecture"
En AuraOps, **el código de fallback mediocre está prohibido**. 
*   **Principio:** Si una operación falla, el sistema debe saber *por qué* y tener una ruta de compensación tipada o un escalado crítico. 
*   **Regla:** Nunca uses bloques `try-catch` para "silenciar" errores o retornar valores `null/empty` que oculten un fallo sistémico.

## 2. Architectural Standards: Hexagonal Architecture
Para garantizar la modularidad, el "Cerebro" (Spring AI) debe implementarse siguiendo el patrón de **Arquitectura Hexagonal (Ports & Adapters)**:
*   **Domain:** Lógica pura de diagnóstico y remediación, libre de dependencias de frameworks.
*   **Ports:** Interfaces que definen cómo el dominio interactúa con el mundo (IA, Kubernetes, Base de Datos).
*   **Adapters:** Implementaciones específicas (Spring AI Adapter, Fabric8 JOSDK Adapter). Esto permite cambiar de GPT-4o a Claude 3.5 o de Kubernetes a una API de Cloud sin tocar el núcleo.

## 3. Java 21 & Spring Boot 3.5 Best Practices
*   **Virtual Threads:** Obligatorio para el procesamiento de streams de logs. No bloquees hilos del sistema.
*   **Records & Sealed Classes:** Utiliza tipos de datos inmutables y jerarquías cerradas para representar estados de salud y tipos de error.
*   **Strict Typing:** Todo evento de observabilidad debe ser mapeado a un objeto fuertemente tipado antes de ser procesado por la IA.

## 4. Testing & Validation (TDD)
No se acepta código sin validación empírica:
*   **Unit Tests:** Cobertura >90% en lógica de dominio.
*   **Integration Tests:** Uso de `Testcontainers` para levantar instancias reales de Kubernetes (K3s) y simular fallos en pods.
*   **Contract Testing:** Uso de `Pact` para asegurar que el JSON generado por la IA es siempre compatible con los parsers del Operador.

## 5. Security First (Zero Trust)
*   **Principio de Mínimo Privilegio:** El RBAC del Operador debe estar limitado estrictamente a los namespaces y recursos bajo su gestión.
*   **Secret Management:** Prohibido usar archivos `.env` o Secrets de K8s planos. Integración obligatoria con HashiCorp Vault.
