package com.kaio.runtracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public class GerarTreinoRequestDTO {

    @NotBlank(message = "O objetivo Ã© obrigatÃ³rio")
    private String objetivo;

    @NotBlank(message = "A experiÃªncia na corrida Ã© obrigatÃ³ria")
    private String experienciaCorrida;

    @NotBlank(message = "O volume semanal atual Ã© obrigatÃ³rio")
    private String volumeSemanalAtual;

    @NotBlank(message = "O ritmo confortÃ¡vel atual Ã© obrigatÃ³rio")
    private String ritmoConfortavel;

    @NotNull(message = "Informe se possui uma prova marcada")
    private Boolean possuiProva;
    private LocalDate dataProva;
    private String distanciaProva;
    private String outraDistanciaProva;
    private String objetivoProva;
    private String tempoDesejadoProva;
    private String importanciaProva;

    @NotBlank(message = "A distÃ¢ncia alvo Ã© obrigatÃ³ria")
    private String distanciaAlvo;

    @NotEmpty(message = "Selecione pelo menos um dia disponÃ­vel para treinar")
    private List<String> diasDisponiveis;

    private Boolean possuiLesao;
    private String descricaoLesao;

    @NotBlank(message = "A intensidade desejada Ã© obrigatÃ³ria")
    private String intensidadeDesejada;

    private String observacoes;

    public GerarTreinoRequestDTO() {
    }

    public String getObjetivo() {
        return objetivo;
    }

    public void setObjetivo(String objetivo) {
        this.objetivo = objetivo;
    }

    public String getExperienciaCorrida() {
        return experienciaCorrida;
    }

    public void setExperienciaCorrida(String experienciaCorrida) {
        this.experienciaCorrida = experienciaCorrida;
    }

    public String getVolumeSemanalAtual() {
        return volumeSemanalAtual;
    }

    public void setVolumeSemanalAtual(String volumeSemanalAtual) {
        this.volumeSemanalAtual = volumeSemanalAtual;
    }

    public String getRitmoConfortavel() {
        return ritmoConfortavel;
    }

    public void setRitmoConfortavel(String ritmoConfortavel) {
        this.ritmoConfortavel = ritmoConfortavel;
    }

    public Boolean getPossuiProva() {
        return possuiProva;
    }

    public void setPossuiProva(Boolean possuiProva) {
        this.possuiProva = possuiProva;
    }

    public LocalDate getDataProva() {
        return dataProva;
    }

    public void setDataProva(LocalDate dataProva) {
        this.dataProva = dataProva;
    }

    public String getDistanciaProva() {
        return distanciaProva;
    }

    public void setDistanciaProva(String distanciaProva) {
        this.distanciaProva = distanciaProva;
    }

    public String getOutraDistanciaProva() {
        return outraDistanciaProva;
    }

    public void setOutraDistanciaProva(String outraDistanciaProva) {
        this.outraDistanciaProva = outraDistanciaProva;
    }

    public String getObjetivoProva() {
        return objetivoProva;
    }

    public void setObjetivoProva(String objetivoProva) {
        this.objetivoProva = objetivoProva;
    }

    public String getTempoDesejadoProva() {
        return tempoDesejadoProva;
    }

    public void setTempoDesejadoProva(String tempoDesejadoProva) {
        this.tempoDesejadoProva = tempoDesejadoProva;
    }

    public String getImportanciaProva() {
        return importanciaProva;
    }

    public void setImportanciaProva(String importanciaProva) {
        this.importanciaProva = importanciaProva;
    }

    @AssertTrue(message = "A data da prova Ã© obrigatÃ³ria quando existe prova marcada")
    public boolean isDataProvaValida() {
        return !Boolean.TRUE.equals(possuiProva) || dataProva != null;
    }

    @AssertTrue(message = "A distÃ¢ncia da prova Ã© obrigatÃ³ria quando existe prova marcada")
    public boolean isDistanciaProvaValida() {
        return !Boolean.TRUE.equals(possuiProva) || temTexto(distanciaProva);
    }

    @AssertTrue(message = "A importÃ¢ncia da prova Ã© obrigatÃ³ria quando existe prova marcada")
    public boolean isImportanciaProvaValida() {
        return !Boolean.TRUE.equals(possuiProva) || temTexto(importanciaProva);
    }

    @AssertTrue(message = "Informe qual Ã© a distÃ¢ncia da prova")
    public boolean isOutraDistanciaProvaValida() {
        return !Boolean.TRUE.equals(possuiProva)
                || !"Outra".equals(distanciaProva)
                || temTexto(outraDistanciaProva);
    }

    @AssertTrue(message = "O tempo desejado Ã© obrigatÃ³rio para buscar um tempo especÃ­fico")
    public boolean isTempoDesejadoProvaValido() {
        return !Boolean.TRUE.equals(possuiProva)
                || !"Buscar um tempo especÃ­fico".equals(objetivoProva)
                || temTexto(tempoDesejadoProva);
    }

    public String getDistanciaAlvo() {
        return distanciaAlvo;
    }

    public void setDistanciaAlvo(String distanciaAlvo) {
        this.distanciaAlvo = distanciaAlvo;
    }

    public List<String> getDiasDisponiveis() {
        return diasDisponiveis;
    }

    public void setDiasDisponiveis(List<String> diasDisponiveis) {
        this.diasDisponiveis = diasDisponiveis;
    }

    public Boolean getPossuiLesao() {
        return possuiLesao;
    }

    public void setPossuiLesao(Boolean possuiLesao) {
        this.possuiLesao = possuiLesao;
    }

    public String getDescricaoLesao() {
        return descricaoLesao;
    }

    public void setDescricaoLesao(String descricaoLesao) {
        this.descricaoLesao = descricaoLesao;
    }

    public String getIntensidadeDesejada() {
        return intensidadeDesejada;
    }

    public void setIntensidadeDesejada(String intensidadeDesejada) {
        this.intensidadeDesejada = intensidadeDesejada;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}


