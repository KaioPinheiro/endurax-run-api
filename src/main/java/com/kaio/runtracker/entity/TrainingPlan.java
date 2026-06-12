package com.kaio.runtracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "training_plans")
@Getter
@Setter

public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String objetivo;

    private String nivel;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @OneToMany(mappedBy = "trainingPlan")
    @JsonIgnore
    private List<Workout> workouts;
}
