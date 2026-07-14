package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
public class OpenAIService {

    private static final Logger logger =
            LoggerFactory.getLogger(OpenAIService.class);

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAIService(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String enviarPromptPlanoSemanal(String systemPrompt, String userPrompt) {
        validarConfiguracao();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        try {
            String responseBody = restClient.post()
                    .uri(OPENAI_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey.trim()))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = StringUtils.hasText(responseBody)
                    ? objectMapper.readTree(responseBody)
                    : null;
            String content = root == null
                    ? null
                    : root.path("choices").path(0)
                            .path("message").path("content").asText(null);

            if (!StringUtils.hasText(content)) {
                throw erroFormato("A IA retornou uma resposta semanal vazia.");
            }

            return content;
        } catch (GerarTreinoIAException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            logger.error(
                    "JSON semanal inválido: class={}, message={}",
                    exception.getClass().getName(),
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "A IA retornou um plano semanal em formato inválido. Tente novamente.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw tratarErroOpenAI(exception);
        } catch (RestClientException exception) {
            logger.warn("Falha de conexão com a OpenAI no plano semanal: {}",
                    sanitizar(exception.getMessage()));
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O Coach IA está temporariamente indisponível. Tente novamente.",
                    exception
            );
        } catch (Exception exception) {
            logger.error(
                    "Erro inesperado no plano semanal: class={}, message={}",
                    exception.getClass().getName(),
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "Não foi possível processar o plano semanal. Tente novamente.",
                    exception
            );
        }
    }

    private GerarTreinoIAException tratarErroOpenAI(
            RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        logger.warn(
                "OpenAI recusou plano semanal: status={}, model={}, body={}",
                status, model, sanitizar(exception.getResponseBodyAsString())
        );
        return switch (status) {
            case 401 -> new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "A configuração do Coach IA é inválida.", exception);
            case 429 -> new GerarTreinoIAException(
                    TOO_MANY_REQUESTS,
                    "O Coach IA atingiu o limite de uso ou a cota disponível.", exception);
            default -> new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "A OpenAI não conseguiu gerar o plano semanal agora.", exception);
        };
    }

    private void validarConfiguracao() {
        if (!StringUtils.hasText(apiKey)
                || "SUA_CHAVE_OPENAI".equals(apiKey.trim())) {
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O Coach IA ainda não foi configurado neste ambiente.");
        }
        if (!StringUtils.hasText(model)) {
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O modelo do Coach IA não está configurado.");
        }
    }

    private GerarTreinoIAException erroFormato(String detalhe) {
        logger.warn("Plano semanal rejeitado: {}", detalhe);
        return new GerarTreinoIAException(
                BAD_GATEWAY,
                detalhe + " Tente gerar novamente.");
    }

    private String sanitizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "<vazio>";
        }
        String seguro = valor
                .replaceAll("(?i)Bearer\\s+[^\\s\\\"']+", "Bearer [REDACTED]")
                .replaceAll("sk-[A-Za-z0-9_-]{10,}", "[REDACTED_API_KEY]");
        return seguro.length() > 4000
                ? seguro.substring(0, 4000) + "... [truncado]"
                : seguro;
    }
}
