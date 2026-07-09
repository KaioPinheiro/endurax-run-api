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
public class GerarPlanoSemanalRequestDTO {

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

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}


