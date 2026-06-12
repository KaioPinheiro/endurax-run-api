package com.kaio.runtracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "workouts")
@Getter
@Setter
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;

    private String tipo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "dia_semana")
    private String diaSemana;

    @Column(name = "distancia_km")
    private Double distanciaKm;

    @Column(name = "pace_alvo")
    private String paceAlvo;

    private String observacoes;

    @Column(nullable = false)
    private String status;

    @ManyToOne
    @JoinColumn(name = "training_plan_id")
    private TrainingPlan trainingPlan;
}