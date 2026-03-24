# Phase 3: Network Security (Istio & Zero Trust) Backlog

## Objetivo
Implementar una arquitectura Zero Trust asegurando toda la comunicacion en el cluster con mTLS estricto y gestionando los secretos de forma dinamica sin exponerlos en etcd.

## Alcance y decisiones
- Usaremos **Istio** como Service Mesh.
- Se configurara **mTLS Estricto** globalmente o a nivel de namespace (`auraops-system`).
- Usaremos **HashiCorp Vault** para:
  1. Actuar como PKI/CA para emitir los certificados de Istio.
  2. Inyectar secretos dinamicamente (ej. `OPENAI_API_KEY`) en el pod del Analyzer usando *Vault Agent Injector*, evitando los `Secrets` nativos de Kubernetes.
- Todos los componentes deben integrarse limpiamente con nuestro sistema existente basado en Kustomize.

## Backlog priorizado

### P0-1: Instalar y Configurar HashiCorp Vault
Estado: TODO

Trabajo tecnico:
1. Desplegar HashiCorp Vault en modo Dev/Standalone en el cluster local para pruebas (via Helm/Kustomize en la carpeta `infra/security/vault`).
2. Configurar el motor de secretos Key-Value (KV) y guardar los secretos iniciales (ej. credenciales LLM vacias/mockeadas para pruebas).
3. Habilitar la autenticacion de Kubernetes en Vault para que los pods puedan asumir roles basandose en su ServiceAccount.
4. Desplegar el *Vault Agent Injector*.

### P0-2: Inyeccion Dinamica de Secretos en AuraOps
Estado: TODO

Trabajo tecnico:
1. Crear politicas en Vault que permitan a la ServiceAccount de `aura-api-analyzer` leer la ruta de secretos designada.
2. Modificar el `deployment.yaml` de `aura-api-analyzer` anadiendo las anotaciones requeridas (`vault.hashicorp.com/agent-inject: "true"`, etc.).
3. Configurar la aplicacion Spring Boot para que lea las variables de entorno o archivos renderizados por el inyector en lugar de usar referencias a K8s Secrets.
4. Validar que el pod levanta y lee correctamente la configuracion.

### P0-3: Despliegue de Istio y mTLS Estricto
Estado: TODO

Trabajo tecnico:
1. Anadir Istio (via `istioctl` profile minimal o Helm) a la infraestructura.
2. Habilitar inyeccion automatica de sidecars (`istio-injection=enabled`) en el namespace `auraops-system`.
3. Crear un recurso `PeerAuthentication` con modo `STRICT` para forzar mTLS.
4. Validar que la comunicacion entre `aura-operator` y `aura-api-analyzer` solo ocurra a traves de proxies encriptados.

### P1-1: PKI de Vault como CA para Istio
Estado: TODO

Trabajo tecnico:
1. Configurar el motor PKI en Vault.
2. Generar el certificado Root y configurar Istio para delegar la firma de certificados (CSR) a Vault en lugar de usar su CA interna (Citadel).
3. Reiniciar los pods para asegurar que recogen los certificados firmados por Vault.

### P1-2: Integracion de Kiali para Observabilidad de Red
Estado: TODO

Trabajo tecnico:
1. Desplegar Kiali.
2. Conectar Kiali con Istio y con nuestro Prometheus/Tempo (si aplica).
3. Verificar que la topologia de trafico entre el Operator y el Analyzer sea visible en el dashboard.
