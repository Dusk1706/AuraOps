Fase 1: El Cerebro (Spring AI & Análisis de Causa Raíz)
El corazón del sistema es un servicio que analiza telemetría en tiempo real para diagnosticar fallos.

Spring AI 1.0: Integra el motor con modelos de lenguaje (LLM) para realizar Análisis de Causa Raíz (RCA). El sistema enviará fragmentos de logs de error de Grafana Loki a la IA para obtener un diagnóstico en lenguaje natural y una sugerencia de reparación.

Java 21 Virtual Threads: Utiliza hilos virtuales para procesar flujos masivos de logs y métricas de forma paralela sin bloquear el sistema, permitiendo que un solo nodo de AuraOps maneje miles de señales concurrentes.

Custom Spring Boot Starter: Crea una librería interna (aura-observability-starter) que cualquier microservicio de la empresa pueda incluir para autoconfigurar OpenTelemetry, métricas personalizadas y trazado distribuido de forma estandarizada.

Fase 2: El "Healer" (Kubernetes Operator en Java) [? COMPLETADA]
Para demostrar un nivel avanzado, no usaremos scripts simples, sino un Operador nativo de Kubernetes.

Java Operator SDK: Implementa un controlador que "escuche" eventos del cluster. Si la IA detecta que un microservicio tiene un "memory leak", el Operador ejecutará automáticamente un reinicio controlado o un escalado horizontal basándose en la recomendación.

Resilience4j: Configura Circuit Breakers y Rate Limiters dentro del propio AuraOps para evitar que una tormenta de alertas sature el sistema de autoreparación.

Fase 3: La Red (Istio Service Mesh & Seguridad Zero Trust)
Un Senior debe garantizar que la comunicación sea invisible pero segura.

Istio & mTLS Estricto: Configura una malla de servicios donde toda la comunicación entre microservicios esté cifrada automáticamente. Utiliza Kiali integrado en tu dashboard para visualizar la topología de red.

HashiCorp Vault: Integra Vault como la Autoridad de Certificación (CA) para Istio y para inyectar dinámicamente secretos (como API Keys de OpenAI) directamente en la memoria de los pods, eliminando el uso de Secrets de Kubernetes convencionales.

Fase 4: El Dashboard (Angular 19+ & Real-time Web)
El frontend debe ser una consola de comando de alta fidelidad.

Angular Signals API: Utiliza Signals para gestionar el estado de los nodos del cluster en el mapa. Esto permite actualizar solo el componente que cambia (ej. un pod que pasa de verde a rojo) con un rendimiento ultra alto.

WebSockets (STOMP): Implementa una conexión bidireccional para que, en cuanto AuraOps tome una acción correctiva, el usuario lo vea reflejado en el mapa del cluster sin refrescar la página.

Fase 5: Infraestructura como Código (Terraform & GitOps)
Terraform Modules: Crea módulos reutilizables para desplegar la infraestructura completa en AWS (EKS, RDS, MSK para Kafka). El estado de Terraform debe guardarse de forma remota en S3 con bloqueo en DynamoDB para demostrar trabajo en equipo senior.

Helm Charts: Empaqueta AuraOps y sus servicios asociados usando Helm para facilitar despliegues consistentes en diferentes entornos (Dev/Staging/Prod).

Fase 6: DevSecOps & Calidad Senior
Contract Testing (Pact): Implementa pruebas de contrato para asegurar que el microservicio de Análisis no rompa la comunicación con el Operador de Kubernetes al cambiar la estructura del JSON de diagnóstico.

Pipeline de Seguridad: Configura GitHub Actions para ejecutar:

SonarQube: Análisis de calidad y cobertura de código.

Snyk: Escaneo de vulnerabilidades en dependencias de Java y Angular.

Trivy: Escaneo de seguridad de las imágenes Docker antes de subirlas al registro.

Fase 7: Observabilidad LGTM (El Stack Moderno)
Instala y configura el stack completo de Grafana Labs mediante OpenTelemetry :

Loki: Agregación de logs para que la IA los analice.

Grafana: Dashboard unificado.

Tempo: Trazas distribuidas para ver cómo una solicitud viaja por todo el sistema.

Mimir/Prometheus: Métricas de rendimiento del cluster y de la JVM (usando Micrometer)
