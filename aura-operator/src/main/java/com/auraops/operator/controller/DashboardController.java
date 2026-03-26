package com.auraops.operator.controller;

import com.auraops.operator.application.DashboardService;
import com.auraops.operator.infrastructure.realtime.HealerEventMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/nodes")
    public List<DashboardService.ClusterNodeDto> getNodes() {
        return dashboardService.getClusterNodes();
    }

    @GetMapping("/events")
    public List<HealerEventMessage> getEvents() {
        return dashboardService.getRecentEvents();
    }
}
