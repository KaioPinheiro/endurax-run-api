package com.kaio.runtracker.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.agent.AgentExecutionContext;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanoTreinoReviewPromptBuilderTest {

    private final PlanoTreinoReviewPromptBuilder builder =
            new PlanoTreinoReviewPromptBuilder(new ObjectMapper().findAndRegisterModules());

    @Test
    void semProvaNaoExigeDataProximidadeOuTaperETrataDuracaoComoCiclo() throws Exception {
        GerarPlanoTreinoRequestDTO request = requestBase("Melhorar tempo nos 5 km", "5 km");
        request.setPossuiProva(false);
        request.setDataProva(LocalDate.of(2027, 5, 10));

        String prompt = prompt(request);

        assertThat(prompt).contains(
                "Se possuiProva=false, não exija data de prova, não avalie proximidade",
                "não exija taper",
                "duracaoSemanas representa somente o ciclo solicitado ao Endurax",
                "necessariamente toda a preparação",
                "Não reprove apenas porque o objetivo completo poderia exigir mais semanas",
                "possuiProva=Não",
                "dataProva=Não se aplica",
                "distanciaProva=Não se aplica");
        assertThat(prompt).doesNotContain("2027-05-10");
    }

    @Test
    void comProvaMantemContextoTemporalDisponivel() throws Exception {
        GerarPlanoTreinoRequestDTO request = requestBase(
                "Melhorar tempo na Meia Maratona", "21,1 km");
        request.setPossuiProva(true);
        request.setDataProva(LocalDate.of(2027, 5, 10));
        request.setDistanciaProva("21,1 km");

        String prompt = prompt(request);

        assertThat(prompt).contains(
                "Somente se possuiProva=true considere data, proximidade",
                "possuiProva=Sim",
                "dataProva=2027-05-10",
                "distanciaProva=21,1 km");
    }

    @Test
    void regraDoCicloIndependeDaDistancia() throws Exception {
        List<Cenario> cenarios = List.of(
                new Cenario("Melhorar tempo nos 5 km", "5 km"),
                new Cenario("Melhorar tempo nos 10 km", "10 km"),
                new Cenario("Melhorar tempo na Maratona", "42 km"));

        for (Cenario cenario : cenarios) {
            GerarPlanoTreinoRequestDTO request = requestBase(cenario.objetivo(), cenario.distancia());
            request.setPossuiProva(false);

            assertThat(prompt(request)).contains(
                    "Objetivo ou distância desejada NÃO significa que existe prova marcada",
                    "duracaoSemanas representa somente o ciclo solicitado ao Endurax",
                    "duracaoSemanas=4");
        }
    }

    @Test
    void forneceContextoSuficienteSemInventarDadoOpcional() throws Exception {
        GerarPlanoTreinoRequestDTO request = requestBase(
                "Melhorar tempo na Maratona", "42 km");
        request.setPossuiProva(false);
        request.setObservacoes(null);

        String prompt = prompt(request);

        assertThat(prompt).contains(
                "objetivo=Melhorar tempo na Maratona",
                "experiencia=Mais de 3 anos",
                "diasDisponiveis=[terça-feira, quinta-feira, sábado, domingo]",
                "diaLongao=domingo",
                "volumeSemanalAtual=40-60 km",
                "maiorDistanciaRealizada=42 km",
                "corre5KmDireto=Sim",
                "tempoAtual=3:20:00",
                "tempoAtual5Km=21:00",
                "tempoDesejado=3:00:00",
                "paceAlvoMeta=4:16 min/km",
                "ritmoConfortavelAtual=4:30-5:00 min/km",
                "possuiLesao=Não",
                "restricaoOuObservacao=Não informado",
                "Dados opcionais ausentes não constituem erro por si próprios");
    }

    @Test
    void diferenciaErroFatalDeWarningSemRemoverBarreiraDeQualidade() {
        String systemPrompt = builder.criarSystemPrompt();

        assertThat(systemPrompt).contains(
                "Use ERROR somente para problema sério que impeça a entrega",
                "risco relevante",
                "carga ou progressão claramente incompatível",
                "Use WARNING para preocupação, incerteza ou melhoria recomendável",
                "Warnings não reprovam sozinhos",
                "não invente limites absolutos");
    }

    private String prompt(GerarPlanoTreinoRequestDTO request) throws Exception {
        return builder.criarPrompt(
                new PlanoTreinoIAResponseDTO(),
                new AgentExecutionContext(request, 4, LocalDate.of(2026, 1, 5), "teste"));
    }

    private GerarPlanoTreinoRequestDTO requestBase(String objetivo, String distanciaAlvo) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo(objetivo);
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "sábado", "domingo"));
        request.setDiaLongao("domingo");
        request.setVolumeSemanalAtual("40-60 km");
        request.setMaiorDistanciaCorrida("42 km");
        request.setCorre5KmSemCaminhar(true);
        request.setTempoAtual("3:20:00");
        request.setTempo5Km("21:00");
        request.setTempoDesejado("3:00:00");
        request.setRitmoConfortavel("4:30-5:00 min/km");
        request.setDistanciaAlvo(distanciaAlvo);
        request.setPossuiLesao(false);
        request.setDuracaoSemanas(4);
        return request;
    }

    private record Cenario(String objetivo, String distancia) {}
}
