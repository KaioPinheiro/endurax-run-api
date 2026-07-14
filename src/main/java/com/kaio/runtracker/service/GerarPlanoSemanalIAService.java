package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoSemanalRequestDTO;
import com.kaio.runtracker.dto.PlanoSemanalIAResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class GerarPlanoSemanalIAService {

    private final PlanoSemanalPromptBuilder promptBuilder;
    private final OpenAIService openAIService;
    private final PlanoRespostaParser respostaParser;

    public GerarPlanoSemanalIAService(
            PlanoSemanalPromptBuilder promptBuilder,
            OpenAIService openAIService,
            PlanoRespostaParser respostaParser) {
        this.promptBuilder = promptBuilder;
        this.openAIService = openAIService;
        this.respostaParser = respostaParser;
    }

    public PlanoSemanalIAResponseDTO gerarPlano(
            GerarPlanoSemanalRequestDTO request) {
        String resposta = openAIService.enviarPromptPlanoSemanal(
                promptBuilder.criarSystemPrompt(),
                promptBuilder.criarPrompt(request)
        );
        return respostaParser.parsePlanoSemanal(resposta);
    }
}
