# Phase 2 Runbook

## Objetivo
Levantar el operador de AuraOps con su analyzer, Loki y Tempo, y validar un flujo de autoremediación sobre Kubernetes.

## Stack local
Desde la raíz del repo:

```powershell
docker compose up --build
```

Servicios expuestos:

- `aura-api-analyzer`: `http://localhost:8080`
- `aura-operator`: `http://localhost:8081`
- `loki`: `http://localhost:3100`
- `tempo`: `http://localhost:3200`

El compose asume que existe un `kubeconfig` local en `${USERPROFILE}\.kube\config` y lo monta dentro del contenedor del operador.

## Despliegue del operador en cluster
Aplicar manifests:

```powershell
kubectl apply -k deploy
```

Crear una política de ejemplo:

```powershell
kubectl apply -f aura-operator/src/main/resources/k8s/healerpolicy-sample.yaml
```

## Validación automatizada
Analyzer:

```powershell
cd aura-api-analyzer
gradle test
```

Operator:

```powershell
cd aura-operator
gradle test
```

La suite del operador incluye:

- pruebas unitarias de política, seguridad y cliente HTTP
- integración del reconciler con Kubernetes simulado
- smoke contra APIs reales de Loki y Tempo
- e2e real con `k3s` y analyzer HTTP en perfil `e2e`

## Perfil e2e del analyzer
La configuración determinística para pruebas vive en la suite de tests y no en el runtime productivo del analyzer.
