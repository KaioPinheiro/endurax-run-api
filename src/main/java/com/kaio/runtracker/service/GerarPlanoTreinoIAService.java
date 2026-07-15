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
        int duracaoSemanas = calcularDuracaoSemanas(request);

        logger.info(
                "Gerando plano completo IA: possuiProva={}, duracaoSemanas={}, model={}",
                Boolean.TRUE.equals(request.getPossuiProva()),
                duracaoSemanas,
                openAIService.getModel()
        );

        String resposta = openAIService.enviarPromptPlanoTreino(
                promptBuilder.criarSystemPrompt(),
                promptBuilder.criarPrompt(request, duracaoSemanas)
        );

        return respostaParser.parsePlanoTreino(resposta, duracaoSemanas);
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
}
