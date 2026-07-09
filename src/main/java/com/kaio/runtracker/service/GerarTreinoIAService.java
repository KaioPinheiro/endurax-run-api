package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarTreinoRequestDTO;
import com.kaio.runtracker.dto.GerarTreinoResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
public class GerarTreinoIAService {

    private static final Logger logger =
            LoggerFactory.getLogger(GerarTreinoIAService.class);

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/chat/completions";

    private static final int LIMITE_BODY_LOG = 4000;

    private static final String SYSTEM_PROMPT = """
            VocÃª Ã© o RunPace Coach, um assistente especializado em corrida de rua, \
            prescriÃ§Ã£o de treinos, recuperaÃ§Ã£o e evoluÃ§Ã£o de performance. Gere treinos \
            seguros, claros e objetivos. NÃ£o substitua orientaÃ§Ã£o mÃ©dica. Caso o usuÃ¡rio \
            informe lesÃ£o, dor importante ou limitaÃ§Ã£o, recomende treino leve, descanso \
            ou avaliaÃ§Ã£o profissional.
            """;

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GerarTreinoIAService(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();

        logger.info(
                "ConfiguraÃ§Ã£o OpenAI carregada: model={}, apiKeyConfigurada={}",
                model,
                chaveConfigurada()
        );
    }

    public GerarTreinoResponseDTO gerarTreino(GerarTreinoRequestDTO request) {
        validarConfiguracao();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", criarUserPrompt(request))
                ),
                "response_format", Map.of("type", "json_object")
        );

        try {
            logger.debug("Enviando solicitaÃ§Ã£o de treino para a OpenAI com model={}", model);

            String respostaBody = restClient.post()
                    .uri(OPENAI_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey.trim()))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode respostaOpenAI = StringUtils.hasText(respostaBody)
                    ? objectMapper.readTree(respostaBody)
                    : null;

            logarEstruturaResposta(respostaOpenAI);
            String conteudo = extrairConteudo(respostaOpenAI);
            if (!StringUtils.hasText(conteudo)) {
                throw new GerarTreinoIAException(
                        BAD_GATEWAY,
                        "A IA nÃ£o conseguiu gerar um treino neste momento. Tente novamente."
                );
            }

            GerarTreinoResponseDTO treino =
                    objectMapper.readValue(conteudo, GerarTreinoResponseDTO.class);

            if (!StringUtils.hasText(treino.getTitulo())
                    || !StringUtils.hasText(treino.getTipo())
                    || !StringUtils.hasText(treino.getDescricao())) {
                throw new GerarTreinoIAException(
                        BAD_GATEWAY,
                        "A IA retornou um treino incompleto. Tente gerar novamente."
                );
            }

            return treino;
        } catch (GerarTreinoIAException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            logarExcecaoInesperada(
                    "Falha ao converter o JSON da OpenAI para GerarTreinoResponseDTO",
                    exception
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "A IA retornou um formato de treino invÃ¡lido. Tente novamente.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw tratarErroOpenAI(exception);
        } catch (RestClientException exception) {
            logger.warn(
                    "Falha de conexÃ£o com a OpenAI usando model={}: {}",
                    model,
                    exception.getMessage()
            );
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O serviÃ§o de IA estÃ¡ temporariamente indisponÃ­vel. Tente novamente mais tarde.",
                    exception
            );
        } catch (Exception exception) {
            logarExcecaoInesperada(
                    "Falha inesperada no fluxo de geraÃ§Ã£o de treino",
                    exception
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "NÃ£o foi possÃ­vel processar o treino gerado. "
                            + "Tente novamente em alguns instantes.",
                    exception
            );
        }
    }

    private void logarEstruturaResposta(JsonNode respostaOpenAI) {
        boolean respostaPresente =
                respostaOpenAI != null && !respostaOpenAI.isNull();
        JsonNode choices = respostaPresente
                ? respostaOpenAI.path("choices")
                : null;
        boolean choicesPresente =
                choices != null && choices.isArray() && !choices.isEmpty();
        JsonNode message = choicesPresente
                ? choices.path(0).path("message")
                : null;
        boolean messagePresente =
                message != null && message.isObject() && !message.isMissingNode();
        JsonNode content = messagePresente
                ? message.path("content")
                : null;
        boolean contentPresente =
                content != null
                        && !content.isMissingNode()
                        && !content.isNull()
                        && StringUtils.hasText(content.asText());

        logger.info(
                "Estrutura da resposta OpenAI: respostaPresente={}, "
                        + "choicesPresente={}, messagePresente={}, contentPresente={}",
                respostaPresente,
                choicesPresente,
                messagePresente,
                contentPresente
        );
    }

    private void logarExcecaoInesperada(
            String contexto,
            Exception exception) {
        String mensagemSegura = sanitizarBody(exception.getMessage());
        StringBuilder stacktraceSeguro = new StringBuilder();

        for (StackTraceElement elemento : exception.getStackTrace()) {
            stacktraceSeguro
                    .append(System.lineSeparator())
                    .append("\tat ")
                    .append(elemento);
        }

        logger.error(
                "{}: exceptionClass={}, message={}, stacktrace={}",
                contexto,
                exception.getClass().getName(),
                mensagemSegura,
                stacktraceSeguro
        );
    }

    private GerarTreinoIAException tratarErroOpenAI(
            RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String requestId = obterRequestId(exception.getResponseHeaders());
        String bodySeguro = sanitizarBody(exception.getResponseBodyAsString());

        logger.warn(
                "OpenAI recusou a solicitaÃ§Ã£o: status={}, model={}, requestId={}, body={}",
                status,
                model,
                requestId,
                bodySeguro
        );

        return switch (status) {
            case 400 -> new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "NÃ£o foi possÃ­vel processar os dados do treino. "
                            + "O payload enviado ao serviÃ§o de IA foi recusado.",
                    exception
            );
            case 401 -> new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "A configuraÃ§Ã£o do Coach IA Ã© invÃ¡lida. "
                            + "Verifique a chave da OpenAI.",
                    exception
            );
            case 429 -> new GerarTreinoIAException(
                    TOO_MANY_REQUESTS,
                    "O Coach IA atingiu o limite de uso ou a cota disponÃ­vel. "
                            + "Tente novamente mais tarde.",
                    exception
            );
            default -> new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "NÃ£o foi possÃ­vel gerar o treino agora. "
                            + "O serviÃ§o de IA recusou a solicitaÃ§Ã£o.",
                    exception
            );
        };
    }

    private void validarConfiguracao() {
        if (!chaveConfigurada()) {
            logger.warn(
                    "ConfiguraÃ§Ã£o OpenAI invÃ¡lida: apiKeyConfigurada=false, model={}",
                    model
            );
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O Coach IA ainda nÃ£o foi configurado neste ambiente."
            );
        }

        if (!StringUtils.hasText(model)) {
            logger.warn("ConfiguraÃ§Ã£o OpenAI invÃ¡lida: model nÃ£o configurado");
            throw new GerarTreinoIAException(
                    SERVICE_UNAVAILABLE,
                    "O modelo do Coach IA nÃ£o estÃ¡ configurado."
            );
        }
    }

    private boolean chaveConfigurada() {
        return StringUtils.hasText(apiKey)
                && !"SUA_CHAVE_OPENAI".equals(apiKey.trim());
    }

    private String obterRequestId(HttpHeaders headers) {
        if (headers == null) {
            return "nÃ£o informado";
        }

        String requestId = headers.getFirst("x-request-id");
        return StringUtils.hasText(requestId) ? requestId : "nÃ£o informado";
    }

    private String sanitizarBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "<vazio>";
        }

        String sanitizado = body
                .replaceAll(
                        "(?i)Bearer\\s+[^\\s\\\"']+",
                        "Bearer [REDACTED]"
                )
                .replaceAll(
                        "sk-[A-Za-z0-9_-]{10,}",
                        "[REDACTED_API_KEY]"
                );

        if (sanitizado.length() > LIMITE_BODY_LOG) {
            return sanitizado.substring(0, LIMITE_BODY_LOG) + "... [truncado]";
        }

        return sanitizado;
    }

    private String extrairConteudo(JsonNode respostaOpenAI) {
        if (respostaOpenAI == null || respostaOpenAI.isNull()) {
            return null;
        }

        return respostaOpenAI.path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText(null);
    }

    private String criarUserPrompt(GerarTreinoRequestDTO request) {
        return """
                Crie um treino de corrida personalizado com base nos dados abaixo:

                Objetivo: %s
                ExperiÃªncia na corrida: %s
                Volume semanal atual: %s
                Ritmo confortÃ¡vel atual: %s
                Data atual: %s
                Possui prova marcada: %s
                Data da prova: %s
                DistÃ¢ncia da prova: %s
                Outra distÃ¢ncia: %s
                Objetivo da prova: %s
                Tempo desejado: %s
                ImportÃ¢ncia da prova: %s
                DistÃ¢ncia alvo: %s
                Dias disponÃ­veis para treinar:
                %s
                Possui lesÃ£o: %s
                DescriÃ§Ã£o da lesÃ£o: %s
                Intensidade desejada: %s
                ObservaÃ§Ãµes: %s

                Antes de montar o treino, analise automaticamente o perfil do atleta e classifique-o como iniciante, intermediário ou avançado utilizando as informações fornecidas.

                Considere principalmente:
                - Experiência na corrida
                - Volume semanal atual
                - Ritmo confortável
                - Distância alvo
                - Objetivo
                - Prova marcada (quando houver)
                - Lesões ou limitações
                - Observações adicionais

                Não utilize autopercepção do atleta para definir essa classificação.

                Utilize essa classificação apenas internamente para definir intensidade, volume, progressão e complexidade do treinamento.

                Regras de personalização:
                - Ajuste intensidade, volume e complexidade Ã  experiÃªncia na corrida informada.
                - Para "Nunca corri", priorize adaptaÃ§Ã£o leve, progressiva e introdutÃ³ria.
                - Para "Ainda nÃ£o corro", crie um treino introdutÃ³rio e progressivo.
                - Para "NÃ£o sei informar", adote uma prescriÃ§Ã£o conservadora.
                - Quanto maior o volume semanal atual, mais estruturado pode ser o treino, respeitando objetivo e lesões.
                - Para ritmo "Ainda nÃ£o sei informar", prescreva por tempo e percepÃ§Ã£o de esforÃ§o, sem exigir pace.
                - Para "Caminhada / trote leve", priorize adaptaÃ§Ã£o progressiva.
                - Quando houver faixa de pace, use-a como referÃªncia para o pace sugerido.
                - Para experiÃªncias maiores, estruture o treino conforme objetivo, perfil inferido e segurança.
                - Gere o treino preferencialmente em um dos dias disponÃ­veis informados.
                - Defina automaticamente duraÃ§Ã£o, distÃ¢ncia e intensidade coerentes com objetivo, experiência, volume, ritmo, distÃ¢ncia alvo, dias disponÃ­veis, prova, lesÃµes e observaÃ§Ãµes.

                Regras de prova e periodizaÃ§Ã£o:
                - Se nÃ£o houver prova marcada ou a data jÃ¡ tiver passado, ignore a prova como objetivo principal.
                - Se faltarem mais de 90 dias, priorize base aerÃ³bica, evoluÃ§Ã£o gradual de volume, resistÃªncia e fortalecimento.
                - Se faltarem entre 31 e 90 dias, inicie a fase especÃ­fica com intervalados, tempo run e longÃµes progressivos adequados ao atleta.
                - Se faltarem entre 8 e 30 dias, refine a preparaÃ§Ã£o, mantenha intensidade, controle volume e evite aumentos bruscos.
                - Se faltarem entre 1 e 7 dias, aplique taper, reduza significativamente o volume e priorize estÃ­mulos leves e recuperaÃ§Ã£o.
                - Para "Prova principal da temporada", organize base, desenvolvimento, especificidade e taper visando pico de performance.
                - Para "Prova importante", priorize a prova sem comprometer totalmente a evoluÃ§Ã£o de longo prazo.
                - Para "Apenas participar", trate a prova como parte do processo, sem periodizaÃ§Ã£o agressiva.
                - Nunca aumente o volume semanal em mais de aproximadamente 10%% em relaÃ§Ã£o ao volume informado.
                - Priorize seguranÃ§a diante de lesÃ£o, fadiga ou limitaÃ§Ã£o.
                - Distribua recuperaÃ§Ã£o entre estÃ­mulos intensos e nÃ£o programe treinos fortes em dias consecutivos.
                - Inclua ao menos um dia semanal de descanso completo ou recuperaÃ§Ã£o ativa.
                - Quando houver prova futura, faÃ§a as decisÃµes ajudarem o atleta a chegar melhor preparado.

                Retorne um treino em JSON vÃ¡lido, sem markdown, no seguinte formato:

                {
                  "titulo": "",
                  "tipo": "",
                  "descricao": "",
                  "distanciaKm": "",
                  "duracaoEstimada": "",
                  "paceSugerido": "",
                  "observacoes": "",
                  "alerta": ""
                }

                Preencha todos os campos como texto. Quando nÃ£o houver alerta especÃ­fico, \
                use uma recomendaÃ§Ã£o geral de seguranÃ§a no campo "alerta".
                """.formatted(
                request.getObjetivo(),
                request.getExperienciaCorrida(),
                request.getVolumeSemanalAtual(),
                request.getRitmoConfortavel(),
                LocalDate.now(),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "NÃ£o",
                request.getDataProva() == null ? "NÃ£o informado" : request.getDataProva(),
                valorOuNaoInformado(request.getDistanciaProva()),
                valorOuNaoInformado(request.getOutraDistanciaProva()),
                valorOuNaoInformado(request.getObjetivoProva()),
                valorOuNaoInformado(request.getTempoDesejadoProva()),
                valorOuNaoInformado(request.getImportanciaProva()),
                request.getDistanciaAlvo(),
                request.getDiasDisponiveis(),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "NÃ£o",
                valorOuNaoInformado(request.getDescricaoLesao()),
                request.getIntensidadeDesejada(),
                valorOuNaoInformado(request.getObservacoes())
        );
    }

    private String valorOuNaoInformado(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "NÃ£o informado";
    }
}


