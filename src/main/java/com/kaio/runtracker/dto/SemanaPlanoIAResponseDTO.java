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
public class SemanaPlanoIAResponseDTO {

    @JsonAlias({"weekNumber", "number"})
    private Integer numeroSemana;

    @JsonAlias("title")
    private String titulo;

    @JsonAlias("focus")
    private String foco;

    @JsonAlias({"workouts", "trainings"})
    private List<TreinoPlanoIAResponseDTO> treinos;
}
