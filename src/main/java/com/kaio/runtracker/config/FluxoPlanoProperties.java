package com.kaio.runtracker.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.fluxo-plano")
public class FluxoPlanoProperties {
    @NotNull(message = "APP_FLUXO_PLANO_MODO deve ser DESENVOLVIMENTO, TESTE ou PRODUCAO")
    private FluxoPlanoModo modo = FluxoPlanoModo.DESENVOLVIMENTO;

    public FluxoPlanoModo getModo() {
        return modo;
    }

    public void setModo(FluxoPlanoModo modo) {
        this.modo = modo;
    }

    public boolean isPagamentoObrigatorio() {
        return modo != FluxoPlanoModo.DESENVOLVIMENTO;
    }

    public boolean isGeracaoDiretaPermitida() {
        return modo == FluxoPlanoModo.DESENVOLVIMENTO;
    }
}
