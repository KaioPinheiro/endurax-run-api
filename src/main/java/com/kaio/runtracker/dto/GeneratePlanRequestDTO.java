package com.kaio.runtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GeneratePlanRequestDTO {

    @NotBlank(message = "O objetivo é obrigatório")
    private String objetivo;

    @NotBlank(message = "A distância é obrigatória")
    private String distancia;

    @NotNull(message = "A quantidade de dias por semana é obrigatória")
    private Integer diasPorSemana;

    @NotBlank(message = "A data da prova é obrigatória")
    private String dataProva;

    private String tempoObjetivo;
    private String observacoes;

    public GeneratePlanRequestDTO() {
    }

    public GeneratePlanRequestDTO(
            String objetivo,
            String distancia,
            Integer diasPorSemana,
            String dataProva,
            String tempoObjetivo,
            String observacoes) {
        this.objetivo = objetivo;
        this.distancia = distancia;
        this.diasPorSemana = diasPorSemana;
        this.dataProva = dataProva;
        this.tempoObjetivo = tempoObjetivo;
        this.observacoes = observacoes;
    }

    public String getObjetivo() {
        return objetivo;
    }

    public String getDistancia() {
        return distancia;
    }

    public Integer getDiasPorSemana() {
        return diasPorSemana;
    }

    public String getDataProva() {
        return dataProva;
    }

    public String getTempoObjetivo() {
        return tempoObjetivo;
    }

    public String getObservacoes() {
        return observacoes;
    }
}