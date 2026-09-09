package com.smartlearning.graph.api;

import com.smartlearning.graph.application.EvidenceImportService;
import com.smartlearning.graph.application.EvidenceReresolutionService;
import com.smartlearning.graph.application.GraphValidationService;
import com.smartlearning.graph.application.GraphVersionService;
import com.smartlearning.infrastructure.health.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminGraphController.class)
@Import(TestSecurityConfig.class)
class AdminGraphCandidateImportAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphVersionService graphVersionService;
    @MockitoBean
    private GraphValidationService graphValidationService;
    @MockitoBean
    private EvidenceImportService evidenceImportService;
    @MockitoBean
    private EvidenceReresolutionService evidenceReresolutionService;

    @Test
    void studentCannotSubmitDerivedCandidateInput() throws Exception {
        mockMvc.perform(post("/api/v1/admin/graph-versions/7/candidate-relation-imports/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"candidates":[{
                                  "candidateId":"candidate-1",
                                  "prerequisiteTopicExternalId":"topic-a",
                                  "dependentTopicExternalId":"topic-b",
                                  "derivationPolicyVersion":"policy-v1",
                                  "candidateStatus":"REVIEW_REQUIRED_NOT_PUBLISHED",
                                  "publishedGraphStatus":"NOT_PUBLISHED",
                                  "rawEvidenceIds":["evidence-1"]
                                }]}
                                """)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }
}
