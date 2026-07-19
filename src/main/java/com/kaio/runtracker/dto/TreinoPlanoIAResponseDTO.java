package com.kaio.runtracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TreinoPlanoIAResponseDTO {

    @JsonAlias({"dayOfWeek", "day"})
    private String diaSemana;

    @JsonAlias("title")
    private String titulo;

    @JsonAlias("type")
    private String tipo;

    @JsonAlias("description")
    private String descricao;

    @JsonAlias({"distanceKm", "distance"})
    private String distanciaKm;

    @JsonAlias({"estimatedDuration", "duration"})
    private String duracaoEstimada;

    @JsonAlias({"suggestedPace", "pace"})
    private String paceSugerido;

    @JsonAlias({"notes", "observations"})
    private String observacoes;

}
