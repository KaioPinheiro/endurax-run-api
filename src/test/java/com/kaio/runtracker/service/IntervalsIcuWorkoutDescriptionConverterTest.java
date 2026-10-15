package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsIcuWorkoutDescriptionConverterTest {
    private final IntervalsIcuWorkoutDescriptionConverter converter =
            new IntervalsIcuWorkoutDescriptionConverter();

    @Test
    void converteAquecimentoRepeticoesRecuperacaoEDesaquecimento() {
        TreinoPlanoIAResponseDTO workout = new TreinoPlanoIAResponseDTO();
        workout.setDescricao("Aquecimento: 10 min de trote leve a 6:20 min/km | "
                + "Principal: 3 x (5 min forte a 4:30 min/km + "
                + "2 min de recuperacao a 6:30 min/km) | "
                + "Desaquecimento: 10 min de caminhada a 7:00 min/km");

        assertThat(converter.convert(workout)).isEqualTo("""
                Aquecimento
                - 10m 6:20/km Pace intensity=warmup

                Principal
                3x
                - 5m 4:30/km Pace intensity=interval
                - 2m 6:30/km Pace intensity=recovery

                Desaquecimento
                - 10m 7:00/km Pace intensity=cooldown""");
    }

    @Test
    void usaDistanciaEIntervaloDePace() {
        TreinoPlanoIAResponseDTO workout = new TreinoPlanoIAResponseDTO();
        workout.setDescricao("Principal: 4 x (800 m a 4:10-4:20 min/km + "
                + "400 m de trote a 6:00 min/km)");

        assertThat(converter.convert(workout))
                .contains("4x", "- 800mtr 4:10-4:20/km Pace",
                        "- 400mtr 6:00/km Pace intensity=recovery");
    }
}
