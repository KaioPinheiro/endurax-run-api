package com.kaio.runtracker.dto;

public class UserDetailDTO {

    private Long id;
    private String nome;
    private String email;
    private String role;

    public UserDetailDTO(Long id, String nome, String email, String role) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.role = role;
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
}
