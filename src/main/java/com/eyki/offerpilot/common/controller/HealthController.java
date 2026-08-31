package com.eyki.offerpilot.common.controller;

import com.eyki.offerpilot.common.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResult<Map<String, Object>> health() {
        return ApiResult.success(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "service", "offer-pilot"
        ));
    }
}