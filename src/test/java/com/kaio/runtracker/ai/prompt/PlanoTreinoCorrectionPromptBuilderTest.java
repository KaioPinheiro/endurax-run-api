package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.service.PlanoTreinoPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanoTreinoCorrectionPromptBuilderTest {

    @Test
    void correcaoPreservaContextoDaMetaEDaCapacidadeAtual() {
        PlanoTreinoCorrectionPromptBuilder builder = new PlanoTreinoCorrectionPromptBuilder(
                new PlanoTreinoPromptBuilder(new PromptObjetivoFactory()));
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo na Maratona");
        request.setDistanciaAlvo("42 km");
        request.setTempoDesejado("3:00:00");
        request.setRitmoConfortavel("4:30-5:00 min/km");
        request.setDiasDisponiveis(List.of("terca-feira", "quinta-feira", "sabado", "domingo"));
        request.setPossuiProva(false);

        String prompt = builder.criarPrompt(
                request, 4, "{}", List.of(), List.of(), List.of(), List.of());

        assertTrue(prompt.contains("objetivo=Melhorar tempo na Maratona"));
        assertTrue(prompt.contains("distância=42 km"));
        assertTrue(prompt.contains("tempoDesejado=3:00:00"));
        assertTrue(prompt.contains("paceAlvoMeta=4:16 min/km"));
        assertTrue(prompt.contains("ritmoConfortavelAtual=4:30-5:00 min/km"));
        assertTrue(prompt.contains("Diferencie-o do ritmo"));
    }
}
