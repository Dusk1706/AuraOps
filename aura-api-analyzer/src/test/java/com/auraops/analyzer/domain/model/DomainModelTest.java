package com.auraops.analyzer.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {

    @Test
    void remediationAction_shouldDefaultNullParametersToEmptyMap() {
        RemediationAction action = new RemediationAction("RESTART", null);

        assertTrue(action.parameters().isEmpty());
    }

    @Test
    void remediationAction_shouldRejectBlankType() {
        assertThrows(IllegalArgumentException.class, () -> new RemediationAction(" ", null));
    }

    @Test
    void telemetry_shouldDefaultNullTracesToEmptyList() {
        Telemetry telemetry = new Telemetry(List.of("error"), new Metrics("1Gi", "500m", 1), null);

        assertTrue(telemetry.traces().isEmpty());
    }

    @Test
    void telemetry_shouldRejectNullLogs() {
        assertThrows(IllegalArgumentException.class, () -> new Telemetry(null, new Metrics("1Gi", "500m", 1), null));
    }

    @Test
    void telemetry_shouldAllowEmptyLogs() {
        assertDoesNotThrow(() -> new Telemetry(List.of(), new Metrics("1Gi", "500m", 1), null));
    }

    @Test
    void telemetry_shouldRejectNullMetrics() {
        assertThrows(IllegalArgumentException.class, () -> new Telemetry(List.of("error"), null, null));
    }

    @Test
    void resource_shouldNormalizeNullNamespace() {
        Resource resource = new Resource("Pod", "pod-1", null);

        assertEquals("", resource.namespace());
    }

    @Test
    void resource_shouldRejectBlankKindOrName() {
        assertThrows(IllegalArgumentException.class, () -> new Resource("", "pod-1", "default"));
        assertThrows(IllegalArgumentException.class, () -> new Resource("Pod", "", "default"));
    }

    @Test
    void metrics_shouldRejectNegativeRestartCount() {
        assertThrows(IllegalArgumentException.class, () -> new Metrics("1Gi", "500m", -1));
    }

    @Test
    void incident_shouldEnforceRequiredFields() {
        Resource resource = new Resource("Pod", "pod-1", "default");
        Telemetry telemetry = new Telemetry(List.of("error"), new Metrics("1Gi", "500m", 1), null);

        assertThrows(IllegalArgumentException.class, () -> new Incident("", resource, telemetry));
        assertThrows(IllegalArgumentException.class, () -> new Incident("id", null, telemetry));
        assertThrows(IllegalArgumentException.class, () -> new Incident("id", resource, null));
        assertDoesNotThrow(() -> new Incident("id", resource, telemetry));
    }
}
