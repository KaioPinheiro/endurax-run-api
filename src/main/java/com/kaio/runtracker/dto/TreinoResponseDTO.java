package com.kaio.runtracker.dto;

import java.time.LocalDate;

public class TreinoResponseDTO {

    private Long id;
    private LocalDate dataTreino;
    private String tipo;
    private Double distanciaKm;
    private Integer tempoMinutos;
    private String paceMedio;
    private String observacoes;

    public TreinoResponseDTO(Long id, LocalDate dataTreino, String tipo, Double distanciaKm,
                             Integer tempoMinutos, String paceMedio, String observacoes) {
        this.id = id;
        this.dataTreino = dataTreino;
        this.tipo = tipo;
        this.distanciaKm = distanciaKm;
        this.tempoMinutos = tempoMinutos;
        this.paceMedio = paceMedio;
        this.observacoes = observacoes;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDataTreino() {
        return dataTreino;
    }

    public String getTipo() {
        return tipo;
    }

    public Double getDistanciaKm() {
        return distanciaKm;
    }

    public Integer getTempoMinutos() {
        return tempoMinutos;
    }

    public String getPaceMedio() {
        return paceMedio;
    }

    public String getObservacoes() {
        return observacoes;
    }
}