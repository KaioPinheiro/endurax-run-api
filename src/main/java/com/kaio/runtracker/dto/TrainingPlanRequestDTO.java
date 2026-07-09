package com.kaio.runtracker.dto;

import jakarta.validation.constraints.NotBlank;

public class TrainingPlanRequestDTO {

    @NotBlank(message = "O nome do plano é obrigatório")
    private String nome;

    @NotBlank(message = "O objetivo do plano é obrigatório")
    private String objetivo;

    private String nivel;

    @NotBlank(message = "A descrição do plano é obrigatória")
    private String descricao;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getObjetivo() {
        return objetivo;
    }

    public void setObjetivo(String objetivo) {
        this.objetivo = objetivo;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
}
