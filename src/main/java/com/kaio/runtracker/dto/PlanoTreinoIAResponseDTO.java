package com.kaio.runtracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanoTreinoIAResponseDTO {

    @JsonAlias("title")
    private String titulo;

    @JsonAlias("summary")
    private String resumo;

    @JsonAlias({"durationWeeks", "weeksDuration"})
    private Integer duracaoSemanas;

    @JsonAlias({"objectivePlan", "planObjective"})
    private String objetivoPlano;

    @JsonAlias({"warning", "notice"})
    private String alerta;

    @JsonAlias("weeks")
    private List<SemanaPlanoIAResponseDTO> semanas;
}
