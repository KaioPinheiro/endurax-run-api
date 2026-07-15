package com.kaio.runtracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GerarPlanoTreinoRequestDTO {

    @NotBlank(message = "O objetivo é obrigatório")
    private String objetivo;

    @NotBlank(message = "A experiência na corrida é obrigatória")
    private String experienciaCorrida;

    @NotBlank(message = "O volume semanal atual é obrigatório")
    private String volumeSemanalAtual;

    @NotBlank(message = "O ritmo confortável atual é obrigatório")
    private String ritmoConfortavel;

    @NotBlank(message = "A distância alvo é obrigatória")
    private String distanciaAlvo;

    @NotEmpty(message = "Selecione pelo menos um dia disponível para treinar")
    private List<String> diasDisponiveis;

    @NotNull(message = "Informe se possui uma prova marcada")
    private Boolean possuiProva;

    private LocalDate dataProva;
    private String distanciaProva;
    private String objetivoProva;
    private String tempoDesejado;
    private String importanciaProva;
    private Boolean possuiLesao;
    private String observacoes;
    private Integer duracaoSemanas;

    @AssertTrue(message = "A data da prova é obrigatória quando existe prova marcada")
    public boolean isDataProvaValida() {
        return !Boolean.TRUE.equals(possuiProva) || dataProva != null;
    }

    @AssertTrue(message = "A distância da prova é obrigatória quando existe prova marcada")
    public boolean isDistanciaProvaValida() {
        return !Boolean.TRUE.equals(possuiProva) || temTexto(distanciaProva);
    }

    @AssertTrue(message = "A importância da prova é obrigatória quando existe prova marcada")
    public boolean isImportanciaProvaValida() {
        return !Boolean.TRUE.equals(possuiProva) || temTexto(importanciaProva);
    }

    @AssertTrue(message = "A duração deve ser 4, 5 ou 6 semanas quando não existe prova marcada")
    public boolean isDuracaoSemanasValida() {
        return Boolean.TRUE.equals(possuiProva)
                || duracaoSemanas == null
                || duracaoSemanas == 4
                || duracaoSemanas == 5
                || duracaoSemanas == 6;
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
