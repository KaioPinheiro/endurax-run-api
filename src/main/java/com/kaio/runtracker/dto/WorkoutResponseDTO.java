package com.kaio.runtracker.dto;

public class WorkoutResponseDTO {

    private Long id;
    private String titulo;
    private String tipo;
    private String descricao;
    private String diaSemana;
    private Double distanciaKm;
    private String paceAlvo;
    private String observacoes;
    private String status;

    public WorkoutResponseDTO(Long id, String titulo, String tipo, String descricao,
                              String diaSemana, Double distanciaKm, String paceAlvo,
                              String observacoes, String status) {
        this.id = id;
        this.titulo = titulo;
        this.tipo = tipo;
        this.descricao = descricao;
        this.diaSemana = diaSemana;
        this.distanciaKm = distanciaKm;
        this.paceAlvo = paceAlvo;
        this.observacoes = observacoes;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public Double getDistanciaKm() {
        return distanciaKm;
    }

    public String getPaceAlvo() {
        return paceAlvo;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getStatus() {
        return status;
    }
}