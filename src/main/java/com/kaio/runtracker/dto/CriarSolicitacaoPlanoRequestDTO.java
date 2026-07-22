package com.kaio.runtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarSolicitacaoPlanoRequestDTO(
        @NotBlank @Email String email,
        @NotNull @Valid GerarPlanoTreinoRequestDTO formulario) {
}
