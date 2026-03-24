package com.auraops.analyzer.infrastructure.contract;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.spring.spring6.PactVerificationSpring6Provider;
import au.com.dius.pact.provider.spring.spring6.Spring6MockMvcTestTarget;
import com.auraops.analyzer.application.ports.in.AnalyzeIncidentUseCase;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.RemediationAction;
import com.auraops.analyzer.infrastructure.adapters.in.web.AnalysisController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(AnalysisController.class)
@Provider("aura-api-analyzer")
@PactFolder("pacts")
class AnalyzerProviderPactTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeIncidentUseCase analyzeIncidentUseCase;

    @BeforeEach
    void before(PactVerificationContext context) {
        context.setTarget(new Spring6MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring6Provider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("analysis available")
    void analysisAvailable() {
        when(analyzeIncidentUseCase.execute(any())).thenReturn(new AnalysisResult.Success(
            "pact-success-001",
            "OOMKilled after sustained heap pressure",
            0.97,
            new RemediationAction(
                "ROLLING_RESTART",
                Map.of("resource", "Pod/payments-api", "namespace", "payments")
            ),
            "Logs and restart count indicate heap exhaustion with a repeatable recovery action."
        ));
    }

    @State("analysis inconclusive")
    void analysisInconclusive() {
        when(analyzeIncidentUseCase.execute(any())).thenReturn(new AnalysisResult.Inconclusive(
            "pact-inconclusive-001",
            "Low data",
            List.of("traces")
        ));
    }
}
