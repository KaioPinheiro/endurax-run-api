package com.kaio.runtracker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PlanoTreinoIAResponseDTO {

    private String titulo;
    private String resumo;
    private Integer duracaoSemanas;
    private String objetivoPlano;
    private List<SemanaPlanoIAResponseDTO> semanas;
}
