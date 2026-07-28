package com.kaio.runtracker.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VolumeSemanalValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void planoNaoExigeVolumeParaQuemNuncaCorreu() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setExperienciaCorrida("Nunca corri");

        assertThat(request.isVolumeSemanalAtualValido()).isTrue();
    }

    @Test
    void treinoNaoExigeVolumeParaQuemEstaParado() {
        GerarTreinoRequestDTO request = new GerarTreinoRequestDTO();
        request.setExperienciaCorrida("Estou parado(a)");

        assertThat(request.isVolumeSemanalAtualValido()).isTrue();
    }

    @Test
    void planoContinuaExigindoVolumeParaOutrasExperiencias() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setExperienciaCorrida("Corro regularmente");

        assertThat(validator.validateProperty(request, "volumeSemanalAtualValido"))
                .extracting(violation -> violation.getMessage())
                .containsExactly("O volume semanal atual é obrigatório");
    }

    @Test
    void treinoContinuaExigindoVolumeParaOutrasExperiencias() {
        GerarTreinoRequestDTO request = new GerarTreinoRequestDTO();
        request.setExperienciaCorrida("Corro regularmente");

        assertThat(validator.validateProperty(request, "volumeSemanalAtualValido"))
                .extracting(violation -> violation.getMessage())
                .containsExactly("O volume semanal atual é obrigatório");
    }
}
