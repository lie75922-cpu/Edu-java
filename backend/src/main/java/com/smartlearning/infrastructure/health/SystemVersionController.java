package com.smartlearning.infrastructure.health;

import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.common.config.ReleaseProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemVersionController {

    private final ReleaseProperties releaseProperties;

    public SystemVersionController(ReleaseProperties releaseProperties) {
        this.releaseProperties = releaseProperties;
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, String>> version() {
        return ApiResponse.ok(Map.of(
                "service", "edu-backend",
                "version", releaseProperties.buildVersion(),
                "buildTime", releaseProperties.buildTime()
        ));
    }
}
