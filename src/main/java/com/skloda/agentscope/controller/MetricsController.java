package com.skloda.agentscope.controller;

import com.skloda.agentscope.middleware.MetricsCollectorMiddleware;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Metrics controller for middleware audit logs.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    /**
     * Print all aggregated metrics to console
     */
    @PostMapping("/print")
    public Map<String, Object> printMetrics() {
        MetricsCollectorMiddleware.printMetricsSummary();
        return Map.of(
            "status", "success",
            "message", "Metrics printed to console. Check your server logs."
        );
    }

    /**
     * Reset all metrics
     */
    @PostMapping("/reset")
    public Map<String, Object> resetMetrics() {
        MetricsCollectorMiddleware.resetMetrics();
        return Map.of(
            "status", "success",
            "message", "All metrics have been reset."
        );
    }

    /**
     * Get metrics status (simple info)
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
            "endpoints", Map.of(
                "print", "POST /api/metrics/print - Print metrics to console",
                "reset", "POST /api/metrics/reset - Reset all metrics",
                "status", "GET /api/metrics/status - Get this status info"
            ),
            "description", "Middleware metrics and audit logs"
        );
    }
}
