package com.kaio.runtracker.ai;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;

import java.util.Set;

public final class CapacidadeCincoKm {
    private static final Set<String> EXPERIENCIAS_APLICAVEIS = Set.of(
            "Estou parado(a)",
            "Menos de 6 meses",
            "6 meses a 1 ano");
    private static final Set<String> OBJETIVOS_APLICAVEIS = Set.of(
            "Começar a correr",
            "Melhorar condicionamento",
            "Emagrecer",
            "Primeiros 5 km");

    private CapacidadeCincoKm() {
    }

    public static boolean ehAplicavel(GerarPlanoTreinoRequestDTO request) {
        return request != null
                && request.getExperienciaCorrida() != null
                && request.getObjetivo() != null
                && EXPERIENCIAS_APLICAVEIS.contains(request.getExperienciaCorrida())
                && OBJETIVOS_APLICAVEIS.contains(request.getObjetivo());
    }

    public static Boolean respostaAplicavel(GerarPlanoTreinoRequestDTO request) {
        return ehAplicavel(request) ? request.getCorre5KmSemCaminhar() : null;
    }
}
