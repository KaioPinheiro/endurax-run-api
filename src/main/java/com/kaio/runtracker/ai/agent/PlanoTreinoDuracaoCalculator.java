package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.service.GerarTreinoIAException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class PlanoTreinoDuracaoCalculator {
    private final Clock clock;

    public PlanoTreinoDuracaoCalculator() {
        this(Clock.systemDefaultZone());
    }

    public PlanoTreinoDuracaoCalculator(Clock clock) {
        this.clock = clock;
    }

    public int calcular(GerarPlanoTreinoRequestDTO request) {
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
                    "A duração deve ser 4, 5 ou 6 semanas quando não existe prova marcada.");
        }

        LocalDate dataProva = request.getDataProva();
        long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(clock), dataProva);
        if (diasRestantes < 0) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_REQUEST,
                    "A data da prova não pode estar no passado.");
        }
        int semanas = (int) Math.ceil(diasRestantes / 7.0);
        return Math.min(6, Math.max(4, semanas));
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
