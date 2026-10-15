package com.kaio.runtracker.integration;

import com.kaio.runtracker.client.IntervalsIcuOAuthClient;
import com.kaio.runtracker.client.IntervalsIcuTokenResponse;
import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.exception.IntervalsIcuOAuthException;
import com.kaio.runtracker.fixture.IntervalsIcuOAuthFixture;
import com.kaio.runtracker.repository.IntervalsIcuConnectionRepository;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import com.kaio.runtracker.service.IntervalsIcuOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "intervals-icu.oauth.client-id=fixture-client",
        "intervals-icu.oauth.client-secret=fixture-secret-not-real",
        "intervals-icu.oauth.redirect-uri=http://localhost:8080/api/integrations/intervals-icu/callback",
        "intervals-icu.oauth.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Transactional
class IntervalsIcuOAuthLocalFlowIntegrationTest {
    private static final String AUTHORIZATION_CODE = "fixture-code-not-real";
    private static final String PROVIDER_TOKEN = "fixture-provider-token-not-real";

    @Autowired
    private IntervalsIcuOAuthService oauthService;
    @Autowired
    private TrainingPlanRepository trainingPlanRepository;
    @Autowired
    private PagamentoRepository pagamentoRepository;
    @Autowired
    private IntervalsIcuConnectionRepository connectionRepository;
    @MockitoBean
    private IntervalsIcuOAuthClient oauthClient;

    private IntervalsIcuOAuthFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IntervalsIcuOAuthFixture(trainingPlanRepository, pagamentoRepository);
    }

    @Test
    void iniciaEConcluiOAuthComPagamentoEPlanoFicticios() {
        var scenario = fixture.approvedCompletedPlan();
        when(oauthClient.exchangeCode(AUTHORIZATION_CODE)).thenReturn(new IntervalsIcuTokenResponse(
                "Bearer",
                PROVIDER_TOKEN,
                "CALENDAR:WRITE",
                new IntervalsIcuTokenResponse.Athlete("fixture-athlete", "Atleta Ficticio")));

        URI authorizationUri = oauthService.beginAuthorization(scenario.planAccessToken());
        String state = UriComponentsBuilder.fromUri(authorizationUri).build()
                .getQueryParams().getFirst("state");

        assertThat(authorizationUri.getHost()).isEqualTo("intervals.icu");
        assertThat(UriComponentsBuilder.fromUri(authorizationUri).build().getQueryParams())
                .containsEntry("client_id", java.util.List.of("fixture-client"))
                .containsEntry("scope", java.util.List.of("CALENDAR:WRITE"));
        assertThat(state).isNotBlank();
        assertThat(oauthService.connectionStatus(scenario.planAccessToken()).connected()).isFalse();

        var callbackResult = oauthService.completeAuthorization(AUTHORIZATION_CODE, state, null);

        assertThat(callbackResult.connected()).isTrue();
        assertThat(callbackResult.athleteName()).isEqualTo("Atleta Ficticio");
        assertThat(callbackResult.scope()).isEqualTo("CALENDAR:WRITE");
        var storedConnection = connectionRepository.findByPagamentoId(scenario.payment().getId())
                .orElseThrow();
        assertThat(storedConnection.getAccessTokenEncrypted())
                .isNotBlank()
                .isNotEqualTo(PROVIDER_TOKEN);
        assertThat(oauthService.connectionStatus(scenario.planAccessToken()).connected()).isTrue();
        verify(oauthClient).exchangeCode(AUTHORIZATION_CODE);
    }

    @Test
    void mantemAsValidacoesDePagamentoGeracaoEPlano() {
        var scenario = fixture.approvedCompletedPlan();

        scenario.payment().setStatus(PagamentoStatus.PENDING);
        pagamentoRepository.saveAndFlush(scenario.payment());
        assertAccessDenied(scenario.planAccessToken());

        scenario.payment().setStatus(PagamentoStatus.APPROVED);
        scenario.payment().setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        pagamentoRepository.saveAndFlush(scenario.payment());
        assertAccessDenied(scenario.planAccessToken());

        scenario.payment().setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        scenario.payment().setTrainingPlan(null);
        pagamentoRepository.saveAndFlush(scenario.payment());
        assertAccessDenied(scenario.planAccessToken());
    }

    private void assertAccessDenied(String planAccessToken) {
        assertThatThrownBy(() -> oauthService.beginAuthorization(planAccessToken))
                .isInstanceOf(IntervalsIcuOAuthException.class);
    }
}
