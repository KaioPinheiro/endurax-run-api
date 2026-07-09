package com.kaio.runtracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity //"classe representa uma tabela do banco”
@Table(name = "treinos")
@Getter //
@Setter
public class Treino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_treino")
    private LocalDate dataTreino;

    private String tipo;

    @Column(name = "distancia_km")
    private Double distanciaKm;

    @Column(name = "tempo_minutos")
    private Integer tempoMinutos;

    @Column(name = "pace_medio")
    private String paceMedio;

    private String observacoes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
