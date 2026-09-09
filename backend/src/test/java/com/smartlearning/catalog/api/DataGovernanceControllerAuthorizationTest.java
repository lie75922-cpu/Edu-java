package com.smartlearning.catalog.api;

import com.smartlearning.catalog.application.DataGovernanceService;
import com.smartlearning.infrastructure.health.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataGovernanceController.class)
@Import(TestSecurityConfig.class)
class DataGovernanceControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataGovernanceService dataGovernanceService;

    @Test
    void studentCannotReadAdministratorDataGovernance() throws Exception {
        mockMvc.perform(get("/api/v1/admin/data-governance/overview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminReadsPersistedGovernanceOverview() throws Exception {
        when(dataGovernanceService.overview()).thenReturn(new DataGovernanceApi.OverviewResponse(1, 0, 2, 0, 0, 0));

        mockMvc.perform(get("/api/v1/admin/data-governance/overview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))))
                .andExpect(status().isOk());

        verify(dataGovernanceService).overview();
    }
}
