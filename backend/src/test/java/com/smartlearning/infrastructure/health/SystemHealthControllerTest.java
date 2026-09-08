package com.smartlearning.infrastructure.health;

import com.smartlearning.common.config.ReleaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SystemHealthController.class, properties = {
        "app.release.mode=false",
        "app.release.build-version=test",
        "app.release.build-time=2026-09-08T00:00:00Z"
})
@Import({TestSecurityConfig.class, SystemHealthControllerTest.ReleasePropertiesTestConfiguration.class})
class SystemHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldBePublicAndReturnUp() throws Exception {
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void missingApiRouteShouldUseTheStandardNotFoundEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReleaseProperties.class)
    static class ReleasePropertiesTestConfiguration {
    }
}
