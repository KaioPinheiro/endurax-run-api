package com.kaio.runtracker.dto;

public class UserResponseDTO {

    private Long id;
    private String nome;
    private String email;
    private String role;
    private Long trainingPlanId;
    private String trainingPlanNome;

    public UserResponseDTO(Long id, String nome, String email, String role,
                           Long trainingPlanId, String trainingPlanNome) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.role = role;
        this.trainingPlanId = trainingPlanId;
        this.trainingPlanNome = trainingPlanNome;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Long getTrainingPlanId() {
        return trainingPlanId;
    }

    public String getTrainingPlanNome() {
        return trainingPlanNome;
    }
}
