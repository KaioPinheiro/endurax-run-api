package com.kaio.runtracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GerarPlanoTreinoRequestDTO {
    private static final Set<String> OBJETIVOS_PERMITIDOS = Set.of(
            "Começar a correr", "Melhorar condicionamento", "Emagrecer",
            "Primeiros 5 km", "Primeiros 10 km", "Primeira Meia Maratona", "Primeira Maratona",
            "Melhorar tempo nos 5 km", "Melhorar tempo nos 10 km",
            "Melhorar tempo na Meia Maratona", "Melhorar tempo na Maratona");

    @NotBlank(message = "O objetivo é obrigatório")
    private String objetivo;
    private String tempoAtual;

    private Boolean corre5KmSemCaminhar;
    private String tempo5Km;
    private String maiorDistanciaCorrida;

    @NotBlank(message = "A experiência na corrida é obrigatória")
    private String experienciaCorrida;

    private String volumeSemanalAtual;

    @NotBlank(message = "O ritmo confortável atual é obrigatório")
    private String ritmoConfortavel;

    private Integer idade;

    @NotBlank(message = "A distância alvo é obrigatória")
    private String distanciaAlvo;

    @NotEmpty(message = "Selecione pelo menos um dia disponível para treinar")
    private List<String> diasDisponiveis;

    @JsonAlias("longRunDay")
    private String diaLongao;

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

    @AssertTrue(message = "O volume semanal atual é obrigatório")
    public boolean isVolumeSemanalAtualValido() {
        return experienciaSemVolumeSemanal() || temTexto(volumeSemanalAtual);
    }

    @AssertTrue(message = "O objetivo informado não é permitido")
    public boolean isObjetivoValido() {
        return objetivo == null || OBJETIVOS_PERMITIDOS.contains(objetivo);
    }

    @AssertTrue(message = "Objetivos de performance exigem tempo atual e desejado válidos, com melhora")
    public boolean isTemposPerformanceValidos() {
        if (!ehObjetivoPerformance()) return !temTexto(tempoAtual);
        Long atual = tempoEmSegundos(tempoAtual);
        Long desejado = tempoEmSegundos(tempoDesejado);
        return atual != null && desejado != null && desejado < atual;
    }

    public boolean ehObjetivoPerformance() {
        return temTexto(objetivo) && objetivo.startsWith("Melhorar tempo ");
    }

    private Long tempoEmSegundos(String tempo) {
        if (!temTexto(tempo)) return null;
        String[] partes = tempo.trim().split(":");
        boolean maratona = objetivo.endsWith("na Maratona");
        if (partes.length != (maratona ? 3 : 2)) return null;
        try {
            long primeiro = Long.parseLong(partes[0]);
            long minutos = Long.parseLong(partes[partes.length - 2]);
            long segundos = Long.parseLong(partes[partes.length - 1]);
            if (primeiro <= 0 || segundos > 59 || (maratona && minutos > 59)) return null;
            return maratona ? primeiro * 3600 + minutos * 60 + segundos : primeiro * 60 + segundos;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean experienciaSemVolumeSemanal() {
        return "Nunca corri".equalsIgnoreCase(experienciaCorrida)
                || "Estou parado".equalsIgnoreCase(experienciaCorrida)
                || "Estou parado(a)".equalsIgnoreCase(experienciaCorrida);
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
