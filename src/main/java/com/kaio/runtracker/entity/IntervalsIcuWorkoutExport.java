package com.kaio.runtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "intervals_icu_workout_exports")
public class IntervalsIcuWorkoutExport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @Column(name = "workout_key", nullable = false, length = 150)
    private String workoutKey;
    @Column(name = "external_id", nullable = false, unique = true, length = 150)
    private String externalId;
    @Column(name = "intervals_event_id", nullable = false)
    private Long intervalsEventId;
    @Column(name = "workout_name", nullable = false)
    private String workoutName;
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Pagamento getPagamento() { return pagamento; }
    public void setPagamento(Pagamento pagamento) { this.pagamento = pagamento; }
    public String getWorkoutKey() { return workoutKey; }
    public void setWorkoutKey(String workoutKey) { this.workoutKey = workoutKey; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Long getIntervalsEventId() { return intervalsEventId; }
    public void setIntervalsEventId(Long intervalsEventId) { this.intervalsEventId = intervalsEventId; }
    public String getWorkoutName() { return workoutName; }
    public void setWorkoutName(String workoutName) { this.workoutName = workoutName; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
