package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAIServiceTest {

    private MockRestServiceServer server;
    private OpenAIService service;

    @BeforeEach
    void configurar() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("chave-teste");
        properties.setBaseUrl("https://servico.teste");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAIService(
                properties,
                new ObjectMapper(),
                builder.build());
    }

    @Test
    void aceitaRespostaValidaSemFazerChamadaReal() {
        responder("""
                {"choices":[{"message":{"content":"{\\"valid\\":true}"}}]}
                """);

        assertThat(service.enviarPromptRevisaoPlano("sistema", "usuario", 4))
                .isEqualTo("{\"valid\":true}");
        server.verify();
    }

    @Test
    void rejeitaRespostaVazia() {
        responder("""
                {"choices":[{"message":{"content":""}}]}
                """);

        assertThatThrownBy(() ->
                service.enviarPromptPlanoTreino("sistema", "usuario", 4))
                .isInstanceOf(GerarTreinoIAException.class)
                .hasMessageContaining("resposta vazia");
        server.verify();
    }

    @Test
    void rejeitaJsonExternoInvalido() {
        responder("conteudo-invalido");

        assertThatThrownBy(() ->
                service.enviarPromptPlanoTreino("sistema", "usuario", 4))
                .isInstanceOf(GerarTreinoIAException.class)
                .hasMessageContaining("formato inválido");
        server.verify();
    }

    @Test
    void rejeitaChaveAusenteAntesDeEnviarRequisicao() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("");

        OpenAIService serviceSemChave = new OpenAIService(
                properties,
                new ObjectMapper(),
                RestClient.builder().baseUrl(properties.getBaseUrl()).build());

        assertThatThrownBy(() ->
                serviceSemChave.enviarPromptPlanoTreino("sistema", "usuario", 4))
                .isInstanceOf(GerarTreinoIAException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    private void responder(String body) {
        server.expect(once(), requestTo("https://servico.teste/v1/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
