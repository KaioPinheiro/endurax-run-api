package com.kaio.runtracker.ai.agent;

import java.util.ArrayList;
import java.util.List;

public class PlanoTreinoReprovadoException extends RuntimeException {
    private final List<String> errors;

    public PlanoTreinoReprovadoException(
            List<String> errosValidacao,
            List<String> errosRevisao) {
        super("O plano não foi aprovado após todas as tentativas de correção.");
        List<String> reunidos = new ArrayList<>(errosValidacao);
        reunidos.addAll(errosRevisao);
        this.errors = List.copyOf(reunidos);
    }

    public List<String> getErrors() {
        return errors;
    }
}
