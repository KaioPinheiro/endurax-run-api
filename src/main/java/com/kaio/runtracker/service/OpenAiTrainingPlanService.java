package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GeneratePlanRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OpenAiTrainingPlanService {

    private static final String OPENAI_RESPONSES_URL =
            "https://api.openai.com/v1/responses";

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OpenAiTrainingPlanService(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    public JsonNode gerarPlano(GeneratePlanRequestDTO request) {
        validarConfiguracao();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", criarPrompt(request)
        );

        try {
            JsonNode resposta = restTemplate.postForObject(
                    OPENAI_RESPONSES_URL,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            String textoGerado = extrairTextoGerado(resposta);

            if (!StringUtils.hasText(textoGerado)) {
                throw new OpenAiTrainingPlanException(
                        "A OpenAI retornou uma resposta vazia ao gerar o plano."
                );
            }

            JsonNode plano = objectMapper.readTree(textoGerado.trim());

            if (plano == null || plano.isNull() || plano.isEmpty()) {
                throw new OpenAiTrainingPlanException(
                        "A OpenAI retornou um JSON vazio ao gerar o plano."
                );
            }

            return plano;
        } catch (OpenAiTrainingPlanException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            throw new OpenAiTrainingPlanException(
                    "Não foi possível gerar o plano: a OpenAI retornou o status "
                            + exception.getStatusCode().value() + ".",
                    exception
            );
        } catch (JsonProcessingException exception) {
            throw new OpenAiTrainingPlanException(
                    "A OpenAI retornou um conteúdo que não é um JSON válido.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new OpenAiTrainingPlanException(
                    "Não foi possível conectar à OpenAI para gerar o plano.",
                    exception
            );
        }
    }

    private void validarConfiguracao() {
        if (!StringUtils.hasText(apiKey)) {
            throw new OpenAiTrainingPlanException(
                    "A variável de ambiente OPENAI_API_KEY não está configurada."
            );
        }
    }

    private String extrairTextoGerado(JsonNode resposta) {
        if (resposta == null || resposta.isNull()) {
            return null;
        }

        String outputText = resposta.path("output_text").asText();
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        StringBuilder texto = new StringBuilder();

        for (JsonNode item : resposta.path("output")) {
            for (JsonNode conteudo : item.path("content")) {
                if ("output_text".equals(conteudo.path("type").asText())) {
                    String trecho = conteudo.path("text").asText();
                    if (StringUtils.hasText(trecho)) {
                        texto.append(trecho);
                    }
                }
            }
        }

        return texto.toString();
    }

    private String criarPrompt(GeneratePlanRequestDTO request) {
        return """
                Gere um plano de treino para corredor amador em formato JSON válido.
                Não retorne markdown.
                Não retorne explicações.
                Retorne somente JSON.

                Dados do corredor:
                - Objetivo: %s
                - Distância: %s
                - Dias de treino por semana: %s
                - Data da prova: %s
                - Tempo objetivo: %s
                - Observações: %s

                Antes de montar o plano, analise automaticamente o perfil do atleta e classifique-o internamente como iniciante, intermediário ou avançado utilizando as informações fornecidas.

                Considere principalmente:
                - Experiência na corrida, quando informada nas observações
                - Volume semanal atual, quando informado nas observações
                - Ritmo confortável, quando informado nas observações
                - Distância alvo
                - Objetivo
                - Prova marcada
                - Lesão ou limitação, quando informada nas observações
                - Observações adicionais

                Não utilize autopercepção do atleta para definir essa classificação.
                Utilize essa classificação apenas internamente para definir intensidade, volume, progressão e complexidade do treinamento.
                A classificação não deve aparecer para o usuário.

                Formato obrigatório do JSON:
                {
                  "nomePlano": "string",
                  "objetivo": "string",
                  "descricao": "string",
                  "treinos": [
                    {
                      "titulo": "string",
                      "tipo": "Rodagem | Ritmo | Tiro | Longão | Regenerativo | Descanso | Subida",
                      "descricao": "string",
                      "dataPlanejada": "yyyy-MM-dd",
                      "distanciaKm": 0,
                      "paceAlvo": "string",
                      "observacoes": "string"
                    }
                  ]
                }

                Regras obrigatórias:
                - Crie treinos coerentes com o objetivo informado.
                - Use a data da prova como referência.
                - Gere pelo menos 7 treinos.
                - Inclua pelo menos 1 descanso.
                - Distribua bem os estímulos.
                - Evite treinos fortes em dias consecutivos.
                - Para descanso, distanciaKm deve ser 0 e paceAlvo deve ser "-".
                """.formatted(
                request.getObjetivo(),
                request.getDistancia(),
                request.getDiasPorSemana(),
                request.getDataProva(),
                valorOuNaoInformado(request.getTempoObjetivo()),
                valorOuNaoInformado(request.getObservacoes())
        );
    }

    private String valorOuNaoInformado(String valor) {
        return StringUtils.hasText(valor) ? valor : "Não informado";
    }
}
