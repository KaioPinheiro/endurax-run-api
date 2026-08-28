package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.agent.AgentExecutionContext;
import com.kaio.runtracker.ai.agent.PlanoTreinoDuracaoCalculator;
import com.kaio.runtracker.ai.agent.ReviewResult;
import com.kaio.runtracker.ai.agent.TrainingPlanGenerator;
import com.kaio.runtracker.ai.agent.ValidationResult;
import com.kaio.runtracker.ai.prompt.PlanoTreinoCorrectionPromptBuilder;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.text.Normalizer;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GerarPlanoTreinoIAService implements TrainingPlanGenerator {

    private static final Logger logger =
            LoggerFactory.getLogger(GerarPlanoTreinoIAService.class);
    private static final int MAX_TENTATIVAS_GERACAO = 2;

    private final PlanoTreinoPromptBuilder promptBuilder;
    private final PlanoTreinoCorrectionPromptBuilder correctionPromptBuilder;
    private final OpenAIService openAIService;
    private final PlanoTreinoRespostaParser respostaParser;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final PlanoTreinoDuracaoCalculator duracaoCalculator;
    private final PlanoTreinoRegrasDeterministicasValidator regrasValidator;

    @Autowired
    public GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            PlanoTreinoCorrectionPromptBuilder correctionPromptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser,
            ObjectMapper objectMapper) {
        this(
                promptBuilder,
                correctionPromptBuilder,
                openAIService,
                respostaParser,
                Clock.systemDefaultZone(),
                objectMapper);
    }

    GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser) {
        this(promptBuilder, openAIService, respostaParser, Clock.systemDefaultZone());
    }

    public GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser,
            Clock clock) {
        this(
                promptBuilder,
                new PlanoTreinoCorrectionPromptBuilder(promptBuilder),
                openAIService,
                respostaParser,
                clock,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    GerarPlanoTreinoIAService(
            PlanoTreinoPromptBuilder promptBuilder,
            PlanoTreinoCorrectionPromptBuilder correctionPromptBuilder,
            OpenAIService openAIService,
            PlanoTreinoRespostaParser respostaParser,
            Clock clock,
            ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.correctionPromptBuilder = correctionPromptBuilder;
        this.openAIService = openAIService;
        this.respostaParser = respostaParser;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.duracaoCalculator = new PlanoTreinoDuracaoCalculator(clock);
        this.regrasValidator = new PlanoTreinoRegrasDeterministicasValidator(clock);
    }

    public PlanoTreinoIAResponseDTO gerarPlano(GerarPlanoTreinoRequestDTO request) {
        return gerarPlano(request, true, null);
    }

    public PlanoTreinoIAResponseDTO gerarPlanoAutomatico(GerarPlanoTreinoRequestDTO request) {
        return gerarPlano(request, false, null);
    }

    private PlanoTreinoIAResponseDTO gerarPlano(
            GerarPlanoTreinoRequestDTO request,
            boolean logDetalhado,
            Integer duracaoForcada) {
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
            duracaoSemanas = duracaoForcada == null
                    ? calcularDuracaoSemanas(request)
                    : duracaoForcada;
            validarRegrasDeterministicas(request);
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
            String userPrompt = promptBuilder.criarPrompt(
                    request, duracaoSemanas, java.time.LocalDate.now(clock));
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
                            promptParaTentativa(
                                    userPrompt, request, tentativa, ultimaFalhaParser),
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
                    logger.info(
                            "Resposta validada: geracaoId={}, semanas={}, treinos={}, possuiAlerta={}",
                            geracaoId,
                            quantidadeSemanas(plano),
                            quantidadeTreinos(plano),
                            StringUtils.hasText(plano.getAlerta())
                    );
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

    @Override
    public PlanoTreinoIAResponseDTO generate(AgentExecutionContext context) {
        return gerarPlano(context.request(), false, context.duracaoSemanas());
    }

    @Override
    public PlanoTreinoIAResponseDTO correct(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context,
            ValidationResult validacao,
            ReviewResult revisao) {
        try {
            String prompt = correctionPromptBuilder.criarPrompt(
                    context.request(),
                    context.duracaoSemanas(),
                    objectMapper.writeValueAsString(plano),
                    validacao.getErrors(),
                    validacao.getWarnings(),
                    revisao.errors(),
                    revisao.warnings(),
                    context.dataInicio());
            String resposta = openAIService.enviarPromptPlanoTreino(
                    promptBuilder.criarSystemPrompt(),
                    prompt,
                    context.duracaoSemanas());
            return respostaParser.parsePlanoTreino(
                    resposta,
                    context.duracaoSemanas(),
                    context.request().getDiasDisponiveis(),
                    Boolean.TRUE.equals(context.request().getPossuiProva()),
                    context.request().getDiaLongao());
        } catch (JsonProcessingException exception) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível preparar a correção do plano.",
                    exception);
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
        payload.put("corre5KmSemCaminhar", request.getCorre5KmSemCaminhar());
        payload.put("tempo5Km", request.getTempo5Km());
        payload.put("maiorDistanciaCorrida", request.getMaiorDistanciaCorrida());
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
            int tentativa,
            GerarTreinoIAException falhaAnterior) {
        if (tentativa == 1) {
            return userPrompt;
        }

        String orientacaoEstruturaDuracao = orientacaoEstruturaDuracao(falhaAnterior);

        return userPrompt
                + "\n\nCorrecao obrigatoria antes de responder:"
                + "\n- Os dias disponiveis escolhidos pelo usuario sao: "
                + request.getDiasDisponiveis()
                + "."
                + "\n- Normalmente, cada semana deve conter treino de corrida em todos esses dias, sem faltar nenhum."
                + "\n- Retorne tipos variados e no maximo um treino intervalado por semana."
                + "\n- Nao inclua educativos em nenhuma sessao."
                + (StringUtils.hasText(request.getDiaLongao())
                        ? "\n- O longao deve ser no dia: " + request.getDiaLongao() + "."
                        : "")
                + "\n- Nao retorne corrida comum em dias que nao estao nessa lista."
                + (Boolean.TRUE.equals(request.getPossuiProva())
                        ? "\n- Se a prova cair fora desses dias, ela substitui exatamente um treino normal de um dia selecionado; nao crie sessao adicional."
                                + "\n- Identifique a competicao inequivocamente com tipo \"Prova\" e titulo contendo \"Prova\"."
                        : "")
                + orientacaoEstruturaDuracao;
    }

    private String orientacaoEstruturaDuracao(GerarTreinoIAException falhaAnterior) {
        if (falhaAnterior == null || falhaAnterior.getMessage() == null) {
            return "";
        }
        String mensagem = falhaAnterior.getMessage();
        String normalizada = normalizar(mensagem);
        if (!normalizada.contains("motivo=")) {
            return "";
        }

        String instrucao = normalizada.contains("principal_distancia_sem_duracao")
                ? "O bloco principal continha distância sem duração explícita no mesmo passo."
                : normalizada.contains("aquecimento_sem_minutos")
                        ? "O aquecimento não continha duração explícita em minutos inteiros."
                        : normalizada.contains("desaquecimento_sem_minutos")
                                ? "O desaquecimento não continha duração explícita em minutos inteiros."
                                : normalizada.contains("recuperacao_ambigua")
                                        ? "A recuperação não estava identificada e separada de forma inequívoca."
                                        : normalizada.contains("series_nao_reconhecidas")
                                                ? "As múltiplas séries estavam fora da sintaxe canônica com parênteses."
                                                : normalizada.contains("repeticao_nao_reconhecida")
                                                        ? "A repetição estava fora da sintaxe canônica N x (...)."
                                                        : "A descrição usou duração ou estrutura fora da gramática suportada.";

        return "\n- A tentativa anterior foi rejeitada: " + instrucao
                + " Motivo técnico: " + valorLog(mensagem)
                + "\n- Gere novamente usando a gramática canônica e minutos inteiros explícitos em cada bloco e esforço por distância.";
    }

    private boolean deveTentarNovamente(
            GerarTreinoIAException exception,
            int tentativa) {
        return tentativa < MAX_TENTATIVAS_GERACAO
                && exception.getStatus() == HttpStatus.BAD_GATEWAY
                && exception.getMessage() != null;
    }

    int calcularDuracaoSemanas(GerarPlanoTreinoRequestDTO request) {
        return duracaoCalculator.calcular(request);
    }

    void validarDiasMinimosParaMaratona(GerarPlanoTreinoRequestDTO request) {
        validarRegrasDeterministicas(request);
    }

    void validarIdadeMinimaParaMaratona(GerarPlanoTreinoRequestDTO request) {
        validarRegrasDeterministicas(request);
    }

    void validarVolumeSemanalParaMaratona(GerarPlanoTreinoRequestDTO request) {
        validarRegrasDeterministicas(request);
    }

    void validarExperienciaParaMaratona(GerarPlanoTreinoRequestDTO request) {
        validarRegrasDeterministicas(request);
    }

    private void validarRegrasDeterministicas(GerarPlanoTreinoRequestDTO request) {
        try {
            regrasValidator.validarMaratona(request);
        } catch (IllegalArgumentException exception) {
            throw new GerarTreinoIAException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) return "";
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private long tempoMs(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }
}
