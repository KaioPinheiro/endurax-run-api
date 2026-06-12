package com.kaio.runtracker.dto;

public class LoginResponseDTO {

    private String token;
    private Long userId;
    private String nome;
    private String role;

    public LoginResponseDTO(String token, Long userId, String nome, String role) {
        this.token = token;
        this.userId = userId;
        this.nome = nome;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNome() {
        return nome;
    }

    public String getRole() {
        return role;
    }
}
