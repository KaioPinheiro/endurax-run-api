package com.kaio.runtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos")
@Getter
@Setter
@NoArgsConstructor
public class Pagamento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_external_id", nullable = false, unique = true, length = 100)
    private String orderExternalId;
    @Column(name = "external_reference", nullable = false, unique = true, length = 150)
    private String externalReference;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PagamentoStatus status;
    @Column(name = "status_detail", length = 100)
    private String statusDetail;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
    @Column(name = "email_pagador", nullable = false, length = 254)
    private String emailPagador;
    @Column(name = "pix_copia_cola", nullable = false, columnDefinition = "TEXT")
    private String pixCopiaCola;
    @Column(name = "qr_code_base64", nullable = false, columnDefinition = "LONGTEXT")
    private String qrCodeBase64;
    @Column(name = "ticket_url", columnDefinition = "TEXT")
    private String ticketUrl;
    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
    @Column(name = "pago_em")
    private LocalDateTime pagoEm;
    @Column(name = "plano_gerado", nullable = false)
    private boolean planoGerado;

    @Enumerated(EnumType.STRING)
    @Column(name = "geracao_status", nullable = false, length = 30)
    private GeracaoPlanoStatus geracaoStatus = GeracaoPlanoStatus.PENDING;

    @Column(name = "geracao_mensagem", length = 255)
    private String geracaoMensagem;

    @OneToOne
    @JoinColumn(name = "solicitacao_plano_id", unique = true)
    private SolicitacaoPlano solicitacaoPlano;

    @OneToOne
    @JoinColumn(name = "training_plan_id", unique = true)
    private TrainingPlan trainingPlan;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() { atualizadoEm = LocalDateTime.now(); }
}
