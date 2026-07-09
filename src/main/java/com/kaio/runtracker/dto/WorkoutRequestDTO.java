package com.kaio.runtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public class WorkoutRequestDTO {

    @NotBlank(message = "O título do treino é obrigatório")
    private String titulo;

    @NotBlank(message = "O tipo do treino é obrigatório")
    private String tipo;

    @NotBlank(message = "A descrição do treino é obrigatória")
    private String descricao;

    @NotBlank(message = "O dia da semana é obrigatório")
    private String diaSemana;

    @NotNull(message = "A data planejada é obrigatória")
    private LocalDate dataPlanejada;

    @NotNull(message = "A distância é obrigatória")
    @Positive(message = "A distância deve ser maior que zero")
    private Double distanciaKm;

    private String paceAlvo;

    private String observacoes;

    private Long trainingPlanId;

    public Long getTrainingPlanId() {
        return trainingPlanId;
    }

    public void setTrainingPlanId(Long trainingPlanId) {
        this.trainingPlanId = trainingPlanId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalDate getDataPlanejada() {
        return dataPlanejada;
    }

    public void setDataPlanejada(LocalDate dataPlanejada) {
        this.dataPlanejada = dataPlanejada;
    }

    public Double getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(Double distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public String getPaceAlvo() {
        return paceAlvo;
    }

    public void setPaceAlvo(String paceAlvo) {
        this.paceAlvo = paceAlvo;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
}
