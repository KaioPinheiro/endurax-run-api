package com.kaio.runtracker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PlanoSemanalIAResponseDTO {

    private String tituloPlano;
    private String objetivo;
    private String nivel;
    private String observacoesGerais;
    private String alerta;
    private List<TreinoSemanalIAResponseDTO> treinos;
}
