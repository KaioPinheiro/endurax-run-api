package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.service.PlanoTreinoPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void correcaoPreservaDiasSemRemoverTreinoParaResolverIntensidade() {
        GerarPlanoTreinoRequestDTO request = requestBase("Melhorar condicionamento", "5 km");

        String prompt = builder().criarPrompt(
                request,
                4,
                "{}",
                List.of(),
                List.of(),
                List.of("intensidade excessiva no sábado"),
                List.of());

        assertThat(prompt).contains(
                "mantenha cada dia de corrida selecionado exatamente",
                "[terça-feira, quinta-feira, sábado, domingo]",
                "Nenhum desses dias pode desaparecer ou virar descanso",
                "Não crie corrida em dia não selecionado e não duplique dias",
                "exatamente a quantidade de treinos de corrida",
                "o treino em leve; NÃO remova o dia de corrida");
    }

    @Test
    void exigeMenorAlteracaoPreservaPartesValidasEConfereEstruturaFinal() {
        String prompt = builder().criarPrompt(
                requestBase("Primeiros 10 km", "10 km"),
                4,
                "{}",
                List.of(),
                List.of(),
                List.of("ajustar carga"),
                List.of("distribuição pode melhorar"));

        assertThat(prompt).contains(
                "Corrija somente os problemas apontados",
                "Não reconstrua livremente o plano",
                "Faça a menor alteração necessária",
                "Preserve integralmente semanas, dias, treinos e demais partes válidas",
                "Avisos orientam melhorias, mas não justificam reconstruir partes válidas",
                "Mantenha exatamente 4 semanas",
                "Preserve a ordem das semanas",
                "todos os dias selecionados continuam presentes como corrida em cada semana",
                "os problemas apontados foram tratados sem criar novos erros estruturais");
    }

    @Test
    void invariantesSaoGeraisEIndependentesDeObjetivoOuDistancia() {
        List<Cenario> cenarios = List.of(
                new Cenario("Começar a correr", "5 km"),
                new Cenario("Melhorar tempo nos 10 km", "10 km"),
                new Cenario("Primeira Meia Maratona", "21,1 km"),
                new Cenario("Melhorar tempo na Maratona", "42 km"));

        for (Cenario cenario : cenarios) {
            String prompt = builder().criarPrompt(
                    requestBase(cenario.objetivo(), cenario.distancia()),
                    4,
                    "{}",
                    List.of(),
                    List.of(),
                    List.of("corrigir intensidade"),
                    List.of());

            assertThat(prompt).contains(
                    "Nenhum desses dias pode desaparecer ou virar descanso",
                    "Não crie corrida em dia não selecionado",
                    "Mantenha em cada semana exatamente a quantidade de treinos de corrida");
        }
    }

    private PlanoTreinoCorrectionPromptBuilder builder() {
        return new PlanoTreinoCorrectionPromptBuilder(
                new PlanoTreinoPromptBuilder(new PromptObjetivoFactory()));
    }

    private GerarPlanoTreinoRequestDTO requestBase(String objetivo, String distancia) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo(objetivo);
        request.setDistanciaAlvo(distancia);
        request.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "sábado", "domingo"));
        request.setPossuiProva(false);
        return request;
    }

    private record Cenario(String objetivo, String distancia) {}
}
