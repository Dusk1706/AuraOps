package com.auraops.operator.controller;

import com.auraops.operator.application.DashboardService;
import com.auraops.operator.infrastructure.realtime.HealerEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void getNodes_shouldReturnNodesFromService() throws Exception {
        var node = new DashboardService.ClusterNodeDto(
            "test-service", "default", "HEALTHY", "STABLE", 2, 2, "NONE", "now", "diag", 1.0
        );
        when(dashboardService.getClusterNodes()).thenReturn(List.of(node));

        mockMvc.perform(get("/api/dashboard/nodes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].serviceName").value("test-service"))
            .andExpect(jsonPath("$[0].health").value("HEALTHY"));
    }

    @Test
    void getEvents_shouldReturnEventsFromService() throws Exception {
        var event = new HealerEventMessage(
            "RECONCILIATION_STARTED", "p1", "a1", "HIGH", "now", "s1", "n1", "d1", "diag", 1.0, null
        );
        when(dashboardService.getRecentEvents()).thenReturn(List.of(event));

        mockMvc.perform(get("/api/dashboard/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].event_type").value("RECONCILIATION_STARTED"))
            .andExpect(jsonPath("$[0].policy").value("p1"));
    }
}
