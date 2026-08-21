package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

public final class PaceAlvoCalculator {

    private static final Map<String, BigDecimal> DISTANCIAS_METROS = Map.of(
            "Melhorar tempo nos 5 km", new BigDecimal("5000"),
            "Melhorar tempo nos 10 km", new BigDecimal("10000"),
            "Melhorar tempo na Meia Maratona", new BigDecimal("21097.5"),
            "Melhorar tempo na Maratona", new BigDecimal("42195")
    );

    private PaceAlvoCalculator() {
    }

    public static Optional<String> calcular(GerarPlanoTreinoRequestDTO request) {
        if (request == null) {
            return Optional.empty();
        }
        BigDecimal distanciaMetros = DISTANCIAS_METROS.get(request.getObjetivo());
        Long tempoSegundos = tempoEmSegundos(request.getTempoDesejado());
        if (distanciaMetros == null || tempoSegundos == null) {
            return Optional.empty();
        }

        long segundosPorKm = BigDecimal.valueOf(tempoSegundos)
                .multiply(BigDecimal.valueOf(1000))
                .divide(distanciaMetros, 0, RoundingMode.HALF_UP)
                .longValueExact();
        if (segundosPorKm <= 0) {
            return Optional.empty();
        }
        return Optional.of(String.format(
                "%d:%02d min/km", segundosPorKm / 60, segundosPorKm % 60));
    }

    private static Long tempoEmSegundos(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String[] partes = valor.trim().split(":");
        if (partes.length != 2 && partes.length != 3) {
            return null;
        }
        try {
            long horas = partes.length == 3 ? Long.parseLong(partes[0]) : 0;
            long minutos = Long.parseLong(partes[partes.length - 2]);
            long segundos = Long.parseLong(partes[partes.length - 1]);
            if (horas < 0 || minutos < 0 || segundos < 0
                    || segundos > 59 || (partes.length == 3 && minutos > 59)) {
                return null;
            }
            long total = Math.addExact(
                    Math.addExact(Math.multiplyExact(horas, 3600),
                            Math.multiplyExact(minutos, 60)),
                    segundos);
            return total > 0 ? total : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }
}
