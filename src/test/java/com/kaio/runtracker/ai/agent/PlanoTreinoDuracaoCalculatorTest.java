package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanoTreinoDuracaoCalculatorTest {
    @Test
    void provaDentroDoLimiteSemprePossuiSemanaEDiaRepresentaveis() {
        LocalDate segunda = LocalDate.of(2026, 7, 20);
        for (int deslocamentoHoje = 0; deslocamentoHoje < 7; deslocamentoHoje++) {
            LocalDate hoje = segunda.plusDays(deslocamentoHoje);
            for (int diasAteProva = 14; diasAteProva <= 34; diasAteProva++) {
                GerarPlanoTreinoRequestDTO request = requestProva(hoje.plusDays(diasAteProva));
                PlanoTreinoDuracaoCalculator calculator = calculator(hoje);
                int duracao = calculator.calcular(request);
                var contexto = PlanoTreinoCalendario.contexto(request, duracao, hoje);

                assertTrue(contexto.provaDentroDoCiclo());
                assertTrue(contexto.numeroSemanaProva() >= 1);
                assertTrue(contexto.numeroSemanaProva() <= duracao);
                LocalDate dataReconstruida = contexto.inicioSemana1()
                        .plusWeeks(contexto.numeroSemanaProva() - 1L)
                        .plusDays(request.getDataProva().getDayOfWeek().getValue() - 1L);
                assertEquals(request.getDataProva(), dataReconstruida);
            }
        }
    }

    @Test
    void provaAlemDaSextaSemanaGeraSeisSemanasEFicaForaDoCiclo() {
        LocalDate hoje = LocalDate.of(2026, 7, 22);
        GerarPlanoTreinoRequestDTO request = requestProva(LocalDate.of(2026, 9, 30));
        int duracao = calculator(hoje).calcular(request);

        assertEquals(6, duracao);
        assertFalse(PlanoTreinoCalendario.contexto(request, duracao, hoje)
                .provaDentroDoCiclo());
    }

    private PlanoTreinoDuracaoCalculator calculator(LocalDate hoje) {
        return new PlanoTreinoDuracaoCalculator(Clock.fixed(
                hoje.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                ZoneId.of("America/Sao_Paulo")));
    }

    private GerarPlanoTreinoRequestDTO requestProva(LocalDate data) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setPossuiProva(true);
        request.setDataProva(data);
        return request;
    }
}
