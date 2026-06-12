package com.kaio.runtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public class TreinoRequestDTO {

    @NotNull(message = "A data do treino é obrigatória")
    private LocalDate dataTreino;

    @NotBlank(message = "O tipo do treino é obrigatório")
    private String tipo;

    @NotNull(message = "A distância é obrigatória")
    @Positive(message = "A distância deve ser maior que zero")
    private Double distanciaKm;

    @NotNull(message = "O tempo é obrigatório")
    @Positive(message = "O tempo deve ser maior que zero")
    private Integer tempoMinutos;

    private String paceMedio;

    private String observacoes;

    public LocalDate getDataTreino() {
        return dataTreino;
    }

    public void setDataTreino(LocalDate dataTreino) {
        this.dataTreino = dataTreino;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Double getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(Double distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public Integer getTempoMinutos() {
        return tempoMinutos;
    }

    public void setTempoMinutos(Integer tempoMinutos) {
        this.tempoMinutos = tempoMinutos;
    }

    public String getPaceMedio() {
        return paceMedio;
    }

    public void setPaceMedio(String paceMedio) {
        this.paceMedio = paceMedio;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
}