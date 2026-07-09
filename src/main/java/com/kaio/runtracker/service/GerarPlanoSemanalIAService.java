package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoSemanalRequestDTO;
import com.kaio.runtracker.dto.PlanoSemanalIAResponseDTO;
import com.kaio.runtracker.dto.TreinoSemanalIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
public class GerarPlanoSemanalIAService {

    private static final Logger logger =
            LoggerFactory.getLogger(GerarPlanoSemanalIAService.class);

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/chat/completions";

    private static final List<String> DIAS_SEMANA = List.of(
            "segunda-feira", "ter\u00e7a-feira", "quarta-feira", "quinta-feira",
            "sexta-feira", "s\u00e1bado", "domingo"
    );
    private static final Map<String, String> NOMENCLATURAS_TREINO = Map.ofEntries(
            Map.entry("corrida continua", "Corrida cont\u00ednua"),
            Map.entry("corida continua", "Corrida cont\u00ednua"),
            Map.entry("corrrida continua", "Corrida cont\u00ednua"),
            Map.entry("corria continua", "Corrida cont\u00ednua"),
            Map.entry("rodagem", "Corrida cont\u00ednua"),
            Map.entry("corrida leve", "Corrida cont\u00ednua"),
            Map.entry("corrida longa", "Corrida longa"),
            Map.entry("corida longa", "Corrida longa"),
            Map.entry("corrrida longa", "Corrida longa"),
            Map.entry("corria longa", "Corrida longa"),
            Map.entry("longao", "Corrida longa"),
            Map.entry("treino longo", "Corrida longa"),
            Map.entry("treino de velocidade", "Treino de velocidade"),
            Map.entry("velocidade", "Treino de velocidade"),
            Map.entry("velocidadee", "Treino de velocidade"),
            Map.entry("tiro", "Treino de velocidade"),
            Map.entry("tiros", "Treino de velocidade"),
            Map.entry("treino de resistencia", "Treino de resist\u00eancia"),
            Map.entry("resistencia", "Treino de resist\u00eancia"),
            Map.entry("corrida de resistencia", "Treino de resist\u00eancia"),
            Map.entry("intervalado", "Intervalado"),
            Map.entry("interbalado", "Intervalado"),
            Map.entry("intervalada", "Intervalado"),
            Map.entry("treino intervalado", "Intervalado"),
            Map.entry("fartlek", "Fartlek"),
            Map.entry("recuperacao ativa", "Recupera\u00e7\u00e3o ativa"),
            Map.entry("recuperacao", "Recupera\u00e7\u00e3o ativa"),
            Map.entry("mobilidade", "Mobilidade"),
            Map.entry("mobilidadee", "Mobilidade"),
            Map.entry("fortalecimento", "Fortalecimento"),
            Map.entry("fortalescimento", "Fortalecimento"),
            Map.entry("fortalecimento leve", "Fortalecimento"),
            Map.entry("descanso", "Descanso"),
            Map.entry("regenerativo", "Regenerativo"),
            Map.entry("corrida regenerativa", "Regenerativo")
    );

    private static final String SYSTEM_PROMPT = """
            Você é o RunPace Coach, um assistente especializado em corrida de rua, \
            periodização semanal, recuperação e evolução de performance. Gere planos \
            seguros, claros e objetivos. Não substitua orientação médica. Caso o usuário \
            informe lesão, dor importante ou limitação, priorize treinos leves, descanso \
            ou avaliação profissional.
            """;

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GerarPlanoSemanalIAService(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public PlanoSemanalIAResponseDTO gerarPlano(
            GerarPlanoSemanalRequestDTO request) {
        validarConfiguracao();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", criarPrompt(request))
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

            PlanoSemanalIAResponseDTO plano =
                    objectMapper.readValue(content, PlanoSemanalIAResponseDTO.class);
            normalizarEOrdenarTreinos(plano);
            return plano;
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

    private void normalizarEOrdenarTreinos(PlanoSemanalIAResponseDTO plano) {
        if (plano == null) {
            throw erroFormato("A IA retornou um plano semanal vazio.");
        }

        int quantidadeOriginal = plano.getTreinos() == null
                ? 0
                : plano.getTreinos().size();
        List<String> diasRetornados = plano.getTreinos() == null
                ? List.of()
                : plano.getTreinos().stream()
                        .filter(treino -> treino != null
                                && StringUtils.hasText(treino.getDiaSemana()))
                        .map(TreinoSemanalIAResponseDTO::getDiaSemana)
                        .toList();
        logger.info(
                "Plano semanal IA: quantidade de treinos retornada antes da normalização={}",
                quantidadeOriginal
        );
        logger.info("Plano semanal IA: dias retornados pela IA={}", diasRetornados);

        Map<String, TreinoSemanalIAResponseDTO> porDia = new LinkedHashMap<>();
        if (plano.getTreinos() != null) {
            for (TreinoSemanalIAResponseDTO treino : plano.getTreinos()) {
                if (treino == null || !StringUtils.hasText(treino.getDiaSemana())) {
                    logger.warn("Plano semanal IA: treino sem dia da semana ignorado.");
                    continue;
                }

                String diaNormalizado = normalizar(treino.getDiaSemana());
                if (!diaEsperado(diaNormalizado)) {
                    logger.warn(
                            "Plano semanal IA: dia fora do intervalo esperado ignorado={}",
                            treino.getDiaSemana()
                    );
                    continue;
                }

                TreinoSemanalIAResponseDTO anterior = porDia.putIfAbsent(
                        diaNormalizado,
                        treino
                );
                if (anterior != null) {
                    logger.warn(
                            "Plano semanal IA: treino duplicado para o dia {} ignorado.",
                            treino.getDiaSemana()
                    );
                }
            }
        }

        List<String> diasFaltantesPreenchidos = new ArrayList<>();
        List<TreinoSemanalIAResponseDTO> ordenados = new ArrayList<>();
        for (String dia : DIAS_SEMANA) {
            String diaNormalizado = normalizar(dia);
            TreinoSemanalIAResponseDTO treino = porDia.get(diaNormalizado);
            if (treino == null) {
                treino = criarDescanso(dia);
                diasFaltantesPreenchidos.add(dia);
            } else {
                treino.setDiaSemana(dia);
            }
            normalizarNomenclaturas(treino);
            ordenados.add(treino);
        }

        logger.info(
                "Plano semanal IA: dias faltantes preenchidos automaticamente={}",
                diasFaltantesPreenchidos
        );
        logger.info(
                "Plano semanal IA: quantidade final após normalização={}",
                ordenados.size()
        );

        if (ordenados.size() != DIAS_SEMANA.size()
                || ordenados.stream().anyMatch(treino -> treino == null
                || !StringUtils.hasText(treino.getDiaSemana()))) {
            throw erroFormato(
                    "Não foi possível normalizar o plano semanal para os sete dias."
            );
        }

        plano.setTreinos(ordenados);
    }

    private boolean diaEsperado(String diaNormalizado) {
        return DIAS_SEMANA.stream()
                .map(this::normalizar)
                .anyMatch(dia -> dia.equals(diaNormalizado));
    }

    private void normalizarNomenclaturas(TreinoSemanalIAResponseDTO treino) {
        if (treino == null) {
            return;
        }

        treino.setTipo(normalizarCategoriaConhecida(treino.getTipo()));
        treino.setTitulo(normalizarCategoriaConhecida(treino.getTitulo()));
    }

    private String normalizarCategoriaConhecida(String valor) {
        if (!StringUtils.hasText(valor)) {
            return valor;
        }

        String chave = normalizar(valor);
        return NOMENCLATURAS_TREINO.getOrDefault(chave, valor.trim());
    }
    private TreinoSemanalIAResponseDTO criarDescanso(String diaSemana) {
        TreinoSemanalIAResponseDTO treino = new TreinoSemanalIAResponseDTO();
        treino.setDiaSemana(diaSemana);
        treino.setTipo("Descanso");
        treino.setTitulo("Descanso");
        treino.setDescricao("Dia reservado para recuperação.");
        treino.setDistanciaKm("0 km");
        treino.setDuracaoEstimada("Livre");
        treino.setPaceSugerido("Não se aplica");
        treino.setObservacoes("Recuperação para manter consistência no plano.");
        return treino;
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

    private String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
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

    private String criarPrompt(GerarPlanoSemanalRequestDTO request) {
        return """
                Crie um plano semanal de corrida com base nos dados abaixo:

                Objetivo: %s
                Experiência na corrida: %s
                Volume semanal atual: %s
                Ritmo confortável atual: %s
                Data atual: %s
                Possui prova marcada: %s
                Data da prova: %s
                Distância da prova: %s
                Outra distância: %s
                Objetivo da prova: %s
                Tempo desejado: %s
                Importância da prova: %s
                Distância alvo: %s
                Dias disponíveis para treinar:
                %s
                Possui lesão: %s
                Descrição da lesão: %s
                Intensidade desejada: %s
                Observações: %s

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
                A classificação não deve aparecer para o usuário.

                Regras do plano:
                - Todos os títulos e tipos devem estar em português correto.
                - Não utilize abreviações nos títulos ou tipos.
                - Não cometa erros ortográficos como "Corida", "Corrrida", "Corria", "Interbalado", "Fortalescimento", "Mobilidadee", "Resistencia" ou "Velocidadee".
                - Para o campo "tipo" e para títulos que representem categorias conhecidas, utilize exatamente uma das nomenclaturas padronizadas do RunPace: "Corrida contínua", "Corrida longa", "Treino de velocidade", "Treino de resistência", "Intervalado", "Fartlek", "Recuperação ativa", "Mobilidade", "Fortalecimento", "Descanso" ou "Regenerativo".
                - O array "treinos" deve conter obrigatoriamente exatamente 7 itens.
                - Deve existir exatamente um item para cada dia: segunda-feira, terça-feira, quarta-feira, quinta-feira, sexta-feira, sábado e domingo.
                - Nunca omita dias, mesmo quando não houver corrida programada.
                - Em dias sem corrida, retorne um item com tipo "Descanso", "Mobilidade", "Recuperação ativa" ou "Fortalecimento leve".
                - Ordene os itens de segunda-feira a domingo.
                - Gere exatamente 7 dias, de segunda-feira a domingo.
                - Coloque treinos de corrida somente nos dias disponíveis selecionados.
                - Nos dias não selecionados, use descanso, mobilidade, recuperação ativa ou fortalecimento leve.
                - Nunca programe corrida em um dia não selecionado.
                - Defina automaticamente duração, distância e intensidade de cada sessão conforme objetivo, experiência, volume, ritmo, distância alvo, dias disponíveis, prova, lesões e observações.
                - Não gere mais dias fortes do que o adequado ao perfil inferido.
                - Ajuste intensidade, volume e complexidade à experiência na corrida informada.
                - Para "Nunca corri", priorize adaptação leve, progressiva e introdutória.
                - Para "Ainda não corro", crie um plano introdutório e progressivo.
                - Para "Não sei informar", adote uma prescrição conservadora.
                - Quanto maior o volume semanal atual, mais estruturado pode ser o plano, respeitando objetivo e lesões.
                - Para ritmo "Ainda não sei informar", prescreva por tempo e percepção de esforço, sem exigir pace.
                - Para "Caminhada / trote leve", priorize adaptação progressiva.
                - Quando houver faixa de pace, use-a como referência para os paces sugeridos.
                - Se houver fadiga ou lesão, reduza a intensidade.
                - Se não houver prova marcada ou a data já tiver passado, ignore a prova como objetivo principal.
                - Se faltarem mais de 90 dias, priorize base aeróbica, evolução gradual de volume, resistência e fortalecimento.
                - Se faltarem entre 31 e 90 dias, inicie a fase específica com intervalados, tempo run e longões progressivos adequados ao atleta.
                - Se faltarem entre 8 e 30 dias, refine a preparação, mantenha intensidade, controle volume e evite aumentos bruscos.
                - Se faltarem entre 1 e 7 dias, aplique taper, reduza significativamente o volume e priorize estímulos leves e recuperação.
                - Para "Prova principal da temporada", organize base, desenvolvimento, especificidade e taper visando pico de performance.
                - Para "Prova importante", priorize a prova sem comprometer totalmente a evolução de longo prazo.
                - Para "Apenas participar", trate a prova como parte do processo, sem periodização agressiva.
                - Nunca aumente o volume semanal em mais de aproximadamente 10%% em relação ao volume informado.
                - Distribua recuperação entre estímulos intensos e não programe treinos fortes em dias consecutivos.
                - Inclua ao menos um dia semanal de descanso completo ou recuperação ativa.
                - Quando houver prova futura, faça todas as decisões ajudarem o atleta a chegar melhor preparado.
                - Retorne somente JSON válido, sem markdown.

                Formato obrigatório:
                {
                  "tituloPlano": "",
                  "objetivo": "",
                  "observacoesGerais": "",
                  "alerta": "",
                  "treinos": [
                    {
                      "diaSemana": "segunda-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "terça-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "quarta-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "quinta-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "sexta-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "sábado",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "domingo",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    }
                  ]
                }
                """.formatted(
                request.getObjetivo(),
                request.getExperienciaCorrida(),
                request.getVolumeSemanalAtual(),
                request.getRitmoConfortavel(),
                LocalDate.now(),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Não",
                request.getDataProva() == null ? "Não informado" : request.getDataProva(),
                valor(request.getDistanciaProva()),
                valor(request.getOutraDistanciaProva()),
                valor(request.getObjetivoProva()),
                valor(request.getTempoDesejadoProva()),
                valor(request.getImportanciaProva()),
                request.getDistanciaAlvo(), request.getDiasDisponiveis(),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Não",
                valor(request.getDescricaoLesao()),
                request.getIntensidadeDesejada(),
                valor(request.getObservacoes())
        );
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Não informado";
    }
}