package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GerarPlanoTreinoIAService {

    private static final Logger logger =
            LoggerFactory.getLogger(GerarPlanoTreinoIAService.class);
    private static final int MAX_TENTATIVAS_GERACAO = 2;

    private final PlanoTreinoPromptBuilder promptBuilder;
    private final OpenAIService openAIService;
    private final PlanoTreinoRespostaParser respostaParser;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser,
            ObjectMapper objectMapper) {
        this(promptBuilder, openAIService, respostaParser, Clock.systemDefaultZone(), objectMapper);
    }

    GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser) {
        this(promptBuilder, openAIService, respostaParser, Clock.systemDefaultZone());
    }

    GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser,
            Clock clock) {
        this(
                promptBuilder,
                openAIService,
                respostaParser,
                clock,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser,
            Clock clock,
            ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.openAIService = openAIService;
        this.respostaParser = respostaParser;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public PlanoTreinoIAResponseDTO gerarPlano(GerarPlanoTreinoRequestDTO request) {
        return gerarPlano(request, true);
    }

    public PlanoTreinoIAResponseDTO gerarPlanoAutomatico(GerarPlanoTreinoRequestDTO request) {
        return gerarPlano(request, false);
    }

    private PlanoTreinoIAResponseDTO gerarPlano(
            GerarPlanoTreinoRequestDTO request,
            boolean logDetalhado) {
        String geracaoId = UUID.randomUUID().toString().substring(0, 8);
        long inicioTotal = System.nanoTime();
        long validacaoMs = 0;
        long promptMs = 0;
        long openaiMs = 0;
        long parserMs = 0;
        Integer duracaoSemanas = null;

        try {
            if (logDetalhado) {
                logger.info("Request: geracaoId={}\n{}", geracaoId, jsonLog(requestParaLog(request)));
            } else {
                logger.info("Geração automática recebida: geracaoId={}, duracaoSemanas={}, quantidadeDias={}",
                        geracaoId, request.getDuracaoSemanas(),
                        request.getDiasDisponiveis() != null ? request.getDiasDisponiveis().size() : 0);
            }

            long inicioValidacao = System.nanoTime();
            duracaoSemanas = calcularDuracaoSemanas(request);
            validarIdadeMinimaParaMaratona(request);
            validarDiasMinimosParaMaratona(request);
            validarVolumeSemanalParaMaratona(request);
            validarExperienciaParaMaratona(request);
            validacaoMs = tempoMs(inicioValidacao);

            logger.info(
                    "Plano IA validado: geracaoId={}, possuiProva={}, duracaoSemanas={}, model={}, validacaoMs={}",
                    geracaoId,
                    Boolean.TRUE.equals(request.getPossuiProva()),
                    duracaoSemanas,
                    openAIService.getModel(),
                    validacaoMs
            );

            long inicioPrompt = System.nanoTime();
            String systemPrompt = promptBuilder.criarSystemPrompt();
            String userPrompt = promptBuilder.criarPrompt(request, duracaoSemanas);
            promptMs = tempoMs(inicioPrompt);
            logger.debug(
                    "Prompt do plano preparado: geracaoId={}, systemPromptChars={}, userPromptChars={}, promptMs={}",
                    geracaoId, systemPrompt.length(), userPrompt.length(), promptMs
            );

            GerarTreinoIAException ultimaFalhaParser = null;
            for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_GERACAO; tentativa++) {
                logger.info(
                        "Enviando plano para OpenAI: geracaoId={}, tentativa={}/{}, duracaoSemanas={}, diasDisponiveis={}",
                        geracaoId, tentativa, MAX_TENTATIVAS_GERACAO,
                        duracaoSemanas, request.getDiasDisponiveis()
                );
                String resposta;
                long inicioOpenAI = System.nanoTime();
                try {
                    resposta = openAIService.enviarPromptPlanoTreino(
                            systemPrompt,
                            promptParaTentativa(userPrompt, request, tentativa),
                            duracaoSemanas
                    );
                } finally {
                    openaiMs += tempoMs(inicioOpenAI);
                }

                logger.info(
                        "Resposta da OpenAI recebida: geracaoId={}, tentativa={}/{}, respostaChars={}",
                        geracaoId, tentativa, MAX_TENTATIVAS_GERACAO,
                        resposta == null ? 0 : resposta.length()
                );

                long inicioParser = System.nanoTime();
                try {
                    PlanoTreinoIAResponseDTO plano = respostaParser.parsePlanoTreino(
                            resposta,
                            duracaoSemanas,
                            request.getDiasDisponiveis(),
                            Boolean.TRUE.equals(request.getPossuiProva()),
                            request.getDiaLongao()
                    );
                    parserMs += tempoMs(inicioParser);
                    if (logDetalhado) {
                        logger.info("Response: geracaoId={}\n{}", geracaoId, jsonLog(plano));
                    } else {
                        logger.info("Resposta automática validada: geracaoId={}, semanas={}, possuiAlerta={}",
                                geracaoId,
                                plano.getSemanas() != null ? plano.getSemanas().size() : 0,
                                StringUtils.hasText(plano.getAlerta()));
                    }
                    logger.info(
                            "Plano IA gerado com sucesso: geracaoId={}, tentativa={}, semanas={}, treinos={}, possuiAlerta={}, parserMs={}, totalMs={}",
                            geracaoId,
                            tentativa,
                            quantidadeSemanas(plano),
                            quantidadeTreinos(plano),
                            StringUtils.hasText(plano.getAlerta()),
                            parserMs,
                            tempoMs(inicioTotal)
                    );
                    return plano;
                } catch (GerarTreinoIAException exception) {
                    parserMs += tempoMs(inicioParser);
                    ultimaFalhaParser = exception;
                    if (!deveTentarNovamente(exception, tentativa)) {
                        throw exception;
                    }
                    logger.warn(
                            "Plano IA rejeitado: geracaoId={}, tentativa={}/{}, status={}, motivo={}. Nova tentativa sera feita.",
                            geracaoId, tentativa, MAX_TENTATIVAS_GERACAO,
                            exception.getStatus(), valorLog(exception.getMessage())
                    );
                }
            }

            throw ultimaFalhaParser;
        } catch (GerarTreinoIAException exception) {
            logger.warn(
                    "Falha ao gerar plano IA: geracaoId={}, status={}, motivo={}, totalMs={}",
                    geracaoId, exception.getStatus(), valorLog(exception.getMessage()),
                    tempoMs(inicioTotal)
            );
            throw exception;
        } catch (RuntimeException exception) {
            logger.error(
                    "Erro inesperado ao gerar plano IA: geracaoId={}, classe={}, motivo={}, totalMs={}",
                    geracaoId, exception.getClass().getSimpleName(),
                    valorLog(exception.getMessage()), tempoMs(inicioTotal), exception
            );
            throw exception;
        } finally {
            logger.info(
                    "Plano IA metricas finais: geracaoId={}, duracaoSemanas={}, validacaoMs={}, promptMs={}, openaiMs={}, parserMs={}, totalMs={}",
                    geracaoId,
                    duracaoSemanas,
                    validacaoMs,
                    promptMs,
                    openaiMs,
                    parserMs,
                    tempoMs(inicioTotal)
            );
        }
    }

    private int quantidadeSemanas(PlanoTreinoIAResponseDTO plano) {
        return plano.getSemanas() == null ? 0 : plano.getSemanas().size();
    }

    private int quantidadeTreinos(PlanoTreinoIAResponseDTO plano) {
        if (plano.getSemanas() == null) {
            return 0;
        }
        return plano.getSemanas().stream()
                .filter(semana -> semana.getTreinos() != null)
                .mapToInt(semana -> semana.getTreinos().size())
                .sum();
    }

    private String valorLog(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "<nao informado>";
        }
        String seguro = valor.replaceAll("[\\r\\n\\t]+", " ").trim();
        return seguro.length() > 300
                ? seguro.substring(0, 300) + "... [truncado]"
                : seguro;
    }

    private String jsonLog(Object valor) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(valor);
        } catch (JsonProcessingException exception) {
            logger.warn(
                    "Nao foi possivel serializar objeto para log JSON: classe={}, motivo={}",
                    valor == null ? "null" : valor.getClass().getSimpleName(),
                    valorLog(exception.getMessage())
            );
            return "<json indisponivel>";
        }
    }

    private Map<String, Object> requestParaLog(GerarPlanoTreinoRequestDTO request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idade", request.getIdade());
        payload.put("objetivo", request.getObjetivo());
        payload.put("experienciaCorrida", request.getExperienciaCorrida());
        payload.put("volumeSemanalAtual", request.getVolumeSemanalAtual());
        payload.put("ritmoConfortavel", request.getRitmoConfortavel());
        payload.put("distanciaAlvo", request.getDistanciaAlvo());
        payload.put("diasDisponiveis", request.getDiasDisponiveis());
        payload.put("possuiProva", request.getPossuiProva());
        payload.put("dataProva", request.getDataProva());
        payload.put("distanciaProva", request.getDistanciaProva());
        payload.put("objetivoProva", request.getObjetivoProva());
        payload.put("tempoDesejado", request.getTempoDesejado());
        payload.put("importanciaProva", request.getImportanciaProva());
        payload.put("possuiLesao", request.getPossuiLesao());
        payload.put("observacoes", request.getObservacoes());
        payload.put("duracaoSemanas", request.getDuracaoSemanas());
        return payload;
    }

    private String promptParaTentativa(
            String userPrompt,
            GerarPlanoTreinoRequestDTO request,
            int tentativa) {
        if (tentativa == 1) {
            return userPrompt;
        }

        return userPrompt
                + "\n\nCorrecao obrigatoria antes de responder:"
                + "\n- Os dias disponiveis escolhidos pelo usuario sao: "
                + request.getDiasDisponiveis()
                + "."
                + "\n- Cada semana deve conter treino de corrida em todos esses dias, sem faltar nenhum."
                + "\n- Retorne tipos variados e no maximo um treino intervalado por semana."
                + "\n- Nao inclua educativos em nenhuma sessao."
                + (StringUtils.hasText(request.getDiaLongao())
                        ? "\n- O longao deve ser no dia: " + request.getDiaLongao() + "."
                        : "")
                + "\n- Nao retorne corrida comum em dias que nao estao nessa lista."
                + (Boolean.TRUE.equals(request.getPossuiProva())
                        ? "\n- Se a prova cair fora desses dias, a competicao pode aparecer no dia real da prova."
                        : "");
    }

    private boolean deveTentarNovamente(
            GerarTreinoIAException exception,
            int tentativa) {
        return tentativa < MAX_TENTATIVAS_GERACAO
                && exception.getStatus() == HttpStatus.BAD_GATEWAY
                && exception.getMessage() != null;
    }

    int calcularDuracaoSemanas(GerarPlanoTreinoRequestDTO request) {
        if (!Boolean.TRUE.equals(request.getPossuiProva())) {
            Integer duracaoSemanas = request.getDuracaoSemanas();
            if (duracaoSemanas == null) {
                return 4;
            }

            if (duracaoSemanas == 4 || duracaoSemanas == 5 || duracaoSemanas == 6) {
                return duracaoSemanas;
            }

            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "A duração deve ser 4, 5 ou 6 semanas quando não existe prova marcada."
            );
        }

        LocalDate hoje = LocalDate.now(clock);
        LocalDate dataProva = request.getDataProva();
        long diasRestantes = ChronoUnit.DAYS.between(hoje, dataProva);

        if (diasRestantes < 0) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "A data da prova não pode estar no passado."
            );
        }

        int semanas = (int) Math.ceil(diasRestantes / 7.0);
        return Math.min(6, Math.max(4, semanas));
    }

    void validarDiasMinimosParaMaratona(GerarPlanoTreinoRequestDTO request) {
        if (!ehPlanoMaratona(request)) {
            return;
        }

        long diasDisponiveis = request.getDiasDisponiveis() == null
                ? 0
                : request.getDiasDisponiveis().stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalizar)
                        .distinct()
                        .count();

        if (diasDisponiveis < 4) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "Para plano de maratona, selecione pelo menos 4 dias disponiveis para treinar."
            );
        }
    }

    void validarIdadeMinimaParaMaratona(GerarPlanoTreinoRequestDTO request) {
        if (!ehPlanoMaratona(request)) {
            return;
        }

        if (request.getIdade() == null || request.getIdade() < 18) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "Para plano de maratona, a idade minima e 18 anos."
            );
        }
    }

    void validarVolumeSemanalParaMaratona(GerarPlanoTreinoRequestDTO request) {
        if (!ehPlanoMaratona(request)) {
            return;
        }

        if (!volumeMaratonaPermitido(request.getVolumeSemanalAtual())) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "Para plano de maratona, o volume semanal atual deve ser 40-60 km, 60-80 km ou 80+ km."
            );
        }
    }

    void validarExperienciaParaMaratona(GerarPlanoTreinoRequestDTO request) {
        if (!ehPlanoMaratona(request)) {
            return;
        }

        if (!experienciaMaratonaPermitida(request.getExperienciaCorrida())) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "Para plano de maratona, a experiencia na corrida deve ser a partir de 1 a 3 anos."
            );
        }
    }

    private boolean ehPlanoMaratona(GerarPlanoTreinoRequestDTO request) {
        return campoIndicaMaratona(request.getObjetivo())
                || campoIndicaMaratona(request.getDistanciaAlvo())
                || campoIndicaMaratona(request.getDistanciaProva())
                || campoIndicaMaratona(request.getObjetivoProva())
                || campoIndicaMaratona(request.getObservacoes());
    }

    private boolean campoIndicaMaratona(String valor) {
        String texto = normalizar(valor);
        if (!StringUtils.hasText(texto)) {
            return false;
        }

        if (texto.matches(".*\\b42\\s*(km|k|quilometros?)\\b.*")) {
            return true;
        }

        return texto.contains("maratona")
                && !texto.contains("meia maratona")
                && !texto.contains("21 km")
                && !texto.contains("21k");
    }

    private boolean volumeMaratonaPermitido(String valor) {
        String texto = normalizar(valor)
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", "");

        return texto.equals("40-60km")
                || texto.equals("40a60km")
                || texto.equals("40ate60km")
                || texto.equals("60-80km")
                || texto.equals("60a80km")
                || texto.equals("60ate80km")
                || texto.equals("80+km")
                || texto.equals("80km+");
    }

    private boolean experienciaMaratonaPermitida(String valor) {
        String texto = normalizar(valor);

        return texto.contains("1 a 3 anos")
                || texto.contains("1-3 anos")
                || texto.contains("mais de 3 anos")
                || texto.contains("mais que 3 anos")
                || texto.contains("acima de 3 anos");
    }

    private String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "";
        }

        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private long tempoMs(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }
}
