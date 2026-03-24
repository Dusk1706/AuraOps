# Phase 2 Hardening Backlog

## Objetivo
Cerrar los hallazgos de arquitectura y fiabilidad para que Phase 2 sea:
- correcta bajo consistencia eventual de Kubernetes,
- autocontenida en despliegue,
- portable en CI/CD,
- segura en runtime (sin mocks en producción),
- explícita ante falta de telemetría.

## Alcance y decisiones
- El flujo de `docker compose` se mantiene como integración real, no demo con mocks.
- `kubectl apply -k` debe ser autosuficiente y atómico.
- El perfil `e2e` no debe existir en classpath productivo.
- La auto-sanación requiere visibilidad mínima: si no hay telemetría, el resultado debe ser inconcluso.

## Backlog priorizado

### P0-1: Verificación robusta de rollout (Observed Generation)
Estado: DONE

Trabajo técnico:
1. En el reconciliador del operador, bloquear transición a `HEALED` hasta que `status.observedGeneration >= metadata.generation` del Deployment objetivo.
2. En `DeploymentReadinessVerifier`, validar readiness solo para el rollout activo (`ReplicaSet` asociado a la generación actual).
3. Integrar `InformerEventSource` para observar eventos de `ReplicaSet` y evitar lecturas stale.

Criterios de aceptación:
1. No se emite `HEALED` en una ventana donde el Deployment aún no refleja la nueva generación.
2. Tests reproducen condición de carrera y demuestran que no hay falso positivo.
3. Logs de reconciliación incluyen generación esperada/observada y `ReplicaSet` validado.

Definition of Done:
1. Unit + integration tests verdes.
2. Métrica/contador de reintentos por espera de `observedGeneration` expuesta.
3. Documentación de diseño actualizada.

---

### P0-2: Manifiestos autocontenidos con Kustomize por composición
Estado: DONE

Trabajo técnico:
1. Reorganizar manifests a estructura base:
   - `deploy/bases/operator`
   - `deploy/bases/analyzer`
   - `deploy/overlays/local` (si aplica)
   - `deploy/kustomization.yaml` (orquestador raíz)
2. Mover/normalizar RBAC, Service, CRD y referencias para eliminar dependencias externas implícitas.
3. Garantizar que un único `kubectl apply -k deploy` despliega operador + analyzer con wiring correcto.

Criterios de aceptación:
1. `kubectl apply -k deploy` funciona en cluster limpio sin pasos manuales extra.
2. No hay referencias rotas en `kustomization` ni en recursos cruzados.
3. Smoke test valida que ambos servicios quedan operativos y alcanzables.

Definition of Done:
1. E2E de instalación documentado y repetible.
2. Runbook de despliegue actualizado con comandos únicos.
3. Validación en Linux y Windows.

---

### P0-3: Remover mock runtime del analyzer
Estado: DONE

Trabajo técnico:
1. Retirar configuración determinística `e2e` del código productivo del analyzer.
2. Mover fixture de pruebas a:
   - módulo dedicado de testing (ej. `aura-e2e-testing`), o
   - `src/test/java` con wiring exclusivo de test.
3. Configurar entorno local para proveedor real ligero (p. ej. Ollama) con estrategia de no descarga en runtime.

Criterios de aceptación:
1. El artefacto de producción no contiene beans/perfiles de mock.
2. E2E usa adaptador real de Spring AI.
3. Falla controlada y explícita si el proveedor no está disponible (sin fallback silencioso a mock).

Definition of Done:
1. Inspección de jar sin clases de test fixture runtime.
2. Tests de integración actualizados.
3. Runbook local actualizado.

---

### P1-1: Portabilidad total de E2E con Testcontainers + K3s
Estado: DONE

Trabajo técnico:
1. Migrar scripts dependientes de shell/host a suite E2E con Testcontainers K3s.
2. Proveer bootstrap de recursos K8s desde tests.
3. Eliminar dependencia de `cmd.exe`, `%USERPROFILE%`, y rutas específicas de Windows.

Criterios de aceptación:
1. Suite E2E corre igual en Linux/macOS/Windows.
2. Pipeline CI ejecuta E2E en runner Linux sin hacks de entorno.
3. Reportes de fallo incluyen estado de pods/eventos clave.

Definition of Done:
1. Job de CI agregado y verde.
2. Tiempo de suite dentro de presupuesto acordado.
3. Flakiness documentada y mitigada.

---

### P1-2: Patrón de error explícito de telemetría
Estado: DONE

Trabajo técnico:
1. Reemplazar retornos vacíos silenciosos (`List.of()`) por estado semántico `TELEMETRY_UNAVAILABLE`.
2. Ajustar modelo de decisión para emitir resultado `INCONCLUSIVE` cuando falte visibilidad.
3. Asegurar que reconciliador no ejecuta acciones destructivas sin observabilidad mínima.

Criterios de aceptación:
1. Si Loki/Tempo no responde, no se genera recomendación concluyente.
2. El sistema registra y expone motivo técnico de inconclusión.
3. Se emite evento/condición visible para operador humano.

Definition of Done:
1. Contratos API actualizados para incluir nuevo estado.
2. Tests unitarios y de contrato cubren camino `TELEMETRY_UNAVAILABLE`.
3. Dashboard/runbook explican el comportamiento.

## Orden de implementación recomendado
1. P0-3 Remover mock runtime del analyzer.
2. P0-1 Verificación robusta de rollout.
3. P1-2 Error explícito de telemetría.
4. P0-2 Kustomize autocontenido.
5. P1-1 Portabilidad E2E.

Rationale:
- Primero se elimina riesgo de regresión y seguridad en runtime.
- Luego se corrige la decisión de sanación (consistencia eventual + visibilidad).
- Finalmente se endurece empaquetado/despliegue y portabilidad completa.

## Plan de ejecución (2 sprints)

Sprint A (riesgo funcional):
1. P0-3
2. P0-1
3. P1-2

Sprint B (operabilidad/plataforma):
1. P0-2
2. P1-1

## Riesgos y mitigaciones
1. Riesgo: aumento de latencia para marcar `HEALED`.
Mitigación: timeout explícito + backoff y métricas de espera.

2. Riesgo: complejidad de manifests durante refactor.
Mitigación: validar con cluster efímero y pruebas smoke por PR.

3. Riesgo: E2E más lenta con K3s.
Mitigación: particionar suites (`smoke` vs `full`) y paralelizar jobs.

## Checklist de verificación final
1. `HEALED` solo aparece tras `observedGeneration` válida.
2. `kubectl apply -k deploy` levanta sistema completo sin pasos extra.
3. Binario de producción libre de perfiles/beans de mock.
4. E2E portable en Linux/macOS/Windows.
5. Falta de Loki/Tempo produce `INCONCLUSIVE` + señal operativa visible.
