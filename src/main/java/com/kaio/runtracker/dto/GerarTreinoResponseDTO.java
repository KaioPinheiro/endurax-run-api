package com.kaio.runtracker.dto;

public class GerarTreinoResponseDTO {

    private String titulo;
    private String tipo;
    private String descricao;
    private String distanciaKm;
    private String duracaoEstimada;
    private String paceSugerido;
    private String observacoes;
    private String alerta;

    public GerarTreinoResponseDTO() {
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

    public String getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(String distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public String getDuracaoEstimada() {
        return duracaoEstimada;
    }

    public void setDuracaoEstimada(String duracaoEstimada) {
        this.duracaoEstimada = duracaoEstimada;
    }

    public String getPaceSugerido() {
        return paceSugerido;
    }

    public void setPaceSugerido(String paceSugerido) {
        this.paceSugerido = paceSugerido;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public String getAlerta() {
        return alerta;
    }

    public void setAlerta(String alerta) {
        this.alerta = alerta;
    }
}
