package com.kaio.runtracker.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.prompt.PlanoTreinoCorrectionPromptBuilder;
import com.kaio.runtracker.ai.prompt.PlanoTreinoReviewPromptBuilder;
import com.kaio.runtracker.ai.prompt.PromptObjetivoFactory;
import com.kaio.runtracker.config.OpenAiClientConfig;
import com.kaio.runtracker.config.OpenAiProperties;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.service.GerarPlanoTreinoIAService;
import com.kaio.runtracker.service.GerarTreinoIAException;
import com.kaio.runtracker.service.OpenAIService;
import com.kaio.runtracker.service.PlanoTreinoPromptBuilder;
import com.kaio.runtracker.service.PlanoTreinoRespostaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingPlanAgentLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_LIVE_TRAINING_PLAN_PIPELINE",
            matches = "(?i)true")
    void executaPipelineRealSemPagamentoOuPersistencia() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assertThat(apiKey)
                .as("OPENAI_API_KEY deve estar definida para o teste live")
                .isNotBlank();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        properties.setModel(valorAmbiente("OPENAI_MODEL", "gpt-4o-mini"));
        properties.setBaseUrl(valorAmbiente("OPENAI_BASE_URL", "https://api.openai.com"));
        properties.setMaxOutputTokens(7000);
        properties.setConnectTimeoutMs(10000);
        properties.setReadTimeoutMs(120000);

        OpenAIService openAIService = new OpenAIService(
                properties,
                objectMapper,
                new OpenAiClientConfig().openAiRestClient(properties));
        PlanoTreinoPromptBuilder promptBuilder =
                new PlanoTreinoPromptBuilder(new PromptObjetivoFactory());
        PlanoTreinoCorrectionPromptBuilder correctionPromptBuilder =
                new PlanoTreinoCorrectionPromptBuilder(promptBuilder);
        PlanoTreinoRespostaParser respostaParser =
                new PlanoTreinoRespostaParser(objectMapper);
        GerarPlanoTreinoIAService generator = new GerarPlanoTreinoIAService(
                promptBuilder,
                correctionPromptBuilder,
                openAIService,
                respostaParser,
                objectMapper);
        OpenAiTrainingPlanReviewer reviewer = new OpenAiTrainingPlanReviewer(
                openAIService,
                objectMapper,
                new PlanoTreinoReviewPromptBuilder(objectMapper));
        TrainingPlanAgent agent = new TrainingPlanAgent(
                generator,
                reviewer,
                new TrainingPlanValidator(),
                2);

        GerarPlanoTreinoRequestDTO request = fixturePerformanceMaratona();
        AgentExecutionContext context = new AgentExecutionContext(
                request,
                4,
                LocalDate.now(),
                "diagnostico-local");

        try {
            AgentExecutionResult resultado = agent.execute(context);
            assertThat(resultado.plano()).isNotNull();
        } catch (PlanoTreinoReprovadoException reprovado) {
            assertThat(reprovado.getErrors()).isNotEmpty();
        } catch (GerarTreinoIAException rejeicaoDoPipeline) {
            assertThat(rejeicaoDoPipeline.getMessage()).isNotBlank();
        }
    }

    private GerarPlanoTreinoRequestDTO fixturePerformanceMaratona() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo na Maratona");
        request.setTempoAtual("3:20:00");
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("21:00");
        request.setMaiorDistanciaCorrida("42 km");
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setVolumeSemanalAtual("40-60 km");
        request.setRitmoConfortavel("4:30-5:00 min/km");
        request.setIdade(35);
        request.setDistanciaAlvo("42 km");
        request.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "sábado", "domingo"));
        request.setDiaLongao("domingo");
        request.setPossuiProva(false);
        request.setTempoDesejado("3:00:00");
        request.setPossuiLesao(false);
        request.setDuracaoSemanas(4);
        return request;
    }

    private String valorAmbiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
