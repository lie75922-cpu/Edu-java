package com.smartlearning.infrastructure.health;

import com.smartlearning.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "edu-backend",
                "version", "0.1.0-SNAPSHOT",
                "timestamp", Instant.now().toString()
        ));
    }
}
