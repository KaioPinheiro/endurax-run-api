package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class PlanoTreinoCalendario {
    private static final Locale PORTUGUES = Locale.forLanguageTag("pt-BR");

    private PlanoTreinoCalendario() {
    }

    public static ContextoProva contexto(
            GerarPlanoTreinoRequestDTO request,
            int duracaoSemanas,
            LocalDate dataInicio) {
        LocalDate inicioSemana1 = dataInicio.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimCiclo = inicioSemana1.plusWeeks(duracaoSemanas).minusDays(1);
        LocalDate dataProva = request.getDataProva();
        boolean possuiProva = Boolean.TRUE.equals(request.getPossuiProva())
                && dataProva != null;
        boolean provaDentroDoCiclo = possuiProva
                && !dataProva.isBefore(inicioSemana1)
                && !dataProva.isAfter(fimCiclo);
        Integer numeroSemana = provaDentroDoCiclo
                ? Math.toIntExact(ChronoUnit.WEEKS.between(
                        inicioSemana1,
                        dataProva.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) + 1
                : null;
        String diaSemana = provaDentroDoCiclo
                ? dataProva.getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUES)
                : null;
        return new ContextoProva(
                dataInicio, inicioSemana1, fimCiclo, dataProva,
                provaDentroDoCiclo, numeroSemana, diaSemana);
    }

    public record ContextoProva(
            LocalDate dataInicio,
            LocalDate inicioSemana1,
            LocalDate fimCiclo,
            LocalDate dataProva,
            boolean provaDentroDoCiclo,
            Integer numeroSemanaProva,
            String diaSemanaProva) {
    }
}
