package com.kaio.runtracker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SemanaPlanoIAResponseDTO {

    private Integer numeroSemana;
    private String titulo;
    private String foco;
    private List<TreinoPlanoIAResponseDTO> treinos;
}
