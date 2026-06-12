package com.kaio.runtracker.dto;

public class TrainingPlanResponseDTO {

    private Long id;
    private String nome;
    private String objetivo;
    private String nivel;
    private String descricao;

    public TrainingPlanResponseDTO(
            Long id,
            String nome,
            String objetivo,
            String nivel,
            String descricao) {
        this.id = id;
        this.nome = nome;
        this.objetivo = objetivo;
        this.nivel = nivel;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getObjetivo() {
        return objetivo;
    }

    public String getNivel() {
        return nivel;
    }

    public String getDescricao() {
        return descricao;
    }
}



