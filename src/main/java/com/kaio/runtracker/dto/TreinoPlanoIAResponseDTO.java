package com.kaio.runtracker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TreinoPlanoIAResponseDTO {

    private String diaSemana;
    private String titulo;
    private String tipo;
    private String descricao;
    private String distanciaKm;
    private String duracaoEstimada;
    private String paceSugerido;
    private String observacoes;
}
