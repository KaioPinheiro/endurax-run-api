package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    private long tempoMs(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }
}
