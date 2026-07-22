package com.kaio.runtracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CriarPagamentoPixRequestDTO(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Informe um e-mail válido")
        String email,
        Long solicitacaoPlanoId) {

    public CriarPagamentoPixRequestDTO(String email) {
        this(email, null);
    }
}
