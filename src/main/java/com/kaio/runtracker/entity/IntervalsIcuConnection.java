package com.kaio.runtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "intervals_icu_connections")
@Getter
@Setter
@NoArgsConstructor
public class IntervalsIcuConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_id", nullable = false, unique = true)
    private Pagamento pagamento;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "athlete_id", nullable = false, length = 100)
    private String athleteId;

    @Column(name = "athlete_name", length = 255)
    private String athleteName;

    @Column(name = "granted_scope", nullable = false, length = 500)
    private String grantedScope;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
