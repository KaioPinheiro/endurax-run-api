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

import java.util.HashMap;
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
    private final int maxOutputTokens;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAIService(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.max-output-tokens:${OPENAI_MAX_OUTPUT_TOKENS:7000}}")
            int maxOutputTokens,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String enviarPromptPlanoSemanal(String systemPrompt, String userPrompt) {
        return enviarPrompt(
                systemPrompt,
                userPrompt,
                "plano semanal",
                null,
                "A IA retornou uma resposta semanal vazia.",
                "A IA retornou um plano semanal em formato inválido. Tente novamente.",
                "Não foi possível processar o plano semanal. Tente novamente.",
                "A OpenAI não conseguiu gerar o plano semanal agora."
        );
    }

    public String enviarPromptPlanoTreino(String systemPrompt, String userPrompt) {
        return enviarPromptPlanoTreino(systemPrompt, userPrompt, null);
    }

    public String enviarPromptPlanoTreino(
            String systemPrompt,
            String userPrompt,
            Integer duracaoSemanas) {
        return enviarPrompt(
                systemPrompt,
                userPrompt,
                "plano completo",
                duracaoSemanas,
                "A IA retornou uma resposta vazia para o plano completo.",
                "A IA retornou um plano em formato inválido. Tente novamente.",
                "Não foi possível processar o plano. Tente novamente.",
                "A OpenAI não conseguiu gerar o plano agora."
        );
    }

    public String getModel() {
        return model;
    }

    private String enviarPrompt(
            String systemPrompt,
            String userPrompt,
            String contexto,
            Integer duracaoSemanas,
            String mensagemRespostaVazia,
            String mensagemJsonInvalido,
            String mensagemErroInesperado,
            String mensagemErroOpenAI) {
        validarConfiguracao();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("response_format", Map.of("type", "json_object"));
        if (maxOutputTokens > 0) {
            body.put("max_tokens", maxOutputTokens);
        }

        long inicio = System.nanoTime();
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
                throw erroFormato(mensagemRespostaVazia);
            }

            JsonNode usage = root.path("usage");
            logger.info(
                    "OpenAI concluiu {}: model={}, duracaoSemanas={}, tempoMs={}, promptTokens={}, completionTokens={}, totalTokens={}, maxOutputTokens={}",
                    contexto,
                    model,
                    duracaoSemanas,
                    tempoMs(inicio),
                    tokenUsage(usage, "prompt_tokens"),
                    tokenUsage(usage, "completion_tokens"),
                    tokenUsage(usage, "total_tokens"),
                    maxOutputTokens > 0 ? maxOutputTokens : null
            );
            return content;
        } catch (GerarTreinoIAException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            logger.error(
                    "JSON inválido em {}: class={}, message={}",
                    contexto,
                    exception.getClass().getName(),
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    mensagemJsonInvalido,
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw tratarErroOpenAI(exception, contexto, mensagemErroOpenAI);
        } catch (RestClientException exception) {
            logger.warn(
                    "Falha de conexão com a OpenAI em {}: {}",
                    contexto,
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O Coach IA está temporariamente indisponível. Tente novamente.",
                    exception
            );
        } catch (Exception exception) {
            logger.error(
                    "Erro inesperado em {}: class={}, message={}",
                    contexto,
                    exception.getClass().getName(),
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    mensagemErroInesperado,
                    exception
            );
        }
    }

    private GerarTreinoIAException tratarErroOpenAI(
            RestClientResponseException exception,
            String contexto,
            String mensagemErroOpenAI) {
        int status = exception.getStatusCode().value();
        logger.warn(
                "OpenAI recusou {}: status={}, model={}, body={}",
                contexto, status, model, sanitizar(exception.getResponseBodyAsString())
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
                    mensagemErroOpenAI, exception);
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
        logger.warn("Resposta da IA rejeitada: {}", detalhe);
        return new GerarTreinoIAException(
                BAD_GATEWAY,
                detalhe + " Tente gerar novamente.");
    }

    private long tempoMs(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private Integer tokenUsage(JsonNode usage, String campo) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        JsonNode valor = usage.path(campo);
        return valor.isInt() ? valor.asInt() : null;
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
