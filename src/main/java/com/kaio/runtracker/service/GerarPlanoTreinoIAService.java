package com.kaio.runtracker.service;

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

@Service
public class GerarPlanoTreinoIAService {

    private static final Logger logger =
            LoggerFactory.getLogger(GerarPlanoTreinoIAService.class);
    private static final int MAX_TENTATIVAS_GERACAO = 2;

    private final PlanoTreinoPromptBuilder promptBuilder;
    private final OpenAIService openAIService;
    private final PlanoTreinoRespostaParser respostaParser;
    private final Clock clock;

    @Autowired
    public GerarPlanoTreinoIAService(
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
        this.promptBuilder = promptBuilder;
        this.openAIService = openAIService;
        this.respostaParser = respostaParser;
        this.clock = clock;
    }

    public PlanoTreinoIAResponseDTO gerarPlano(GerarPlanoTreinoRequestDTO request) {
        long inicioTotal = System.nanoTime();
        long validacaoMs = 0;
        long promptMs = 0;
        long openaiMs = 0;
        long parserMs = 0;
        Integer duracaoSemanas = null;

        try {
            long inicioValidacao = System.nanoTime();
            duracaoSemanas = calcularDuracaoSemanas(request);
            validarIdadeMinimaParaMaratona(request);
            validarDiasMinimosParaMaratona(request);
            validarVolumeSemanalParaMaratona(request);
            validarExperienciaParaMaratona(request);
            validacaoMs = tempoMs(inicioValidacao);

            logger.info(
                    "Gerando plano completo IA: possuiProva={}, duracaoSemanas={}, model={}",
                    Boolean.TRUE.equals(request.getPossuiProva()),
                    duracaoSemanas,
                    openAIService.getModel()
            );

            long inicioPrompt = System.nanoTime();
            String systemPrompt = promptBuilder.criarSystemPrompt();
            String userPrompt = promptBuilder.criarPrompt(request, duracaoSemanas);
            promptMs = tempoMs(inicioPrompt);

            GerarTreinoIAException ultimaFalhaParser = null;
            for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_GERACAO; tentativa++) {
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

                long inicioParser = System.nanoTime();
                try {
                    return respostaParser.parsePlanoTreino(
                            resposta,
                            duracaoSemanas,
                            request.getDiasDisponiveis()
                    );
                } catch (GerarTreinoIAException exception) {
                    parserMs += tempoMs(inicioParser);
                    ultimaFalhaParser = exception;
                    if (!deveTentarNovamente(exception, tentativa)) {
                        throw exception;
                    }
                    logger.warn(
                            "Plano completo IA: resposta rejeitada na tentativa {}. Nova tentativa sera feita.",
                            tentativa
                    );
                }
            }

            throw ultimaFalhaParser;
        } finally {
            logger.info(
                    "Plano completo IA metricas: duracaoSemanas={}, validacaoMs={}, promptMs={}, openaiMs={}, parserMs={}, totalMs={}",
                    duracaoSemanas,
                    validacaoMs,
                    promptMs,
                    openaiMs,
                    parserMs,
                    tempoMs(inicioTotal)
            );
        }
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
                + "\n- Nao retorne corrida em dias que nao estao nessa lista.";
    }

    private boolean deveTentarNovamente(
            GerarTreinoIAException exception,
            int tentativa) {
        return tentativa < MAX_TENTATIVAS_GERACAO
                && exception.getStatus() == HttpStatus.BAD_GATEWAY
                && exception.getMessage() != null
                && (exception.getMessage().contains("dias escolhidos")
                || exception.getMessage().contains("dia nao selecionado"));
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
