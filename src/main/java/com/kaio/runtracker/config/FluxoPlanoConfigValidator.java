package com.kaio.runtracker.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class FluxoPlanoConfigValidator {
    private final FluxoPlanoProperties fluxoPlanoProperties;
    private final MercadoPagoProperties mercadoPagoProperties;

    public FluxoPlanoConfigValidator(
            FluxoPlanoProperties fluxoPlanoProperties,
            MercadoPagoProperties mercadoPagoProperties) {
        this.fluxoPlanoProperties = fluxoPlanoProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
    }

    @PostConstruct
    public void validar() {
        FluxoPlanoModo modo = fluxoPlanoProperties.getModo();
        if (modo == FluxoPlanoModo.TESTE && !mercadoPagoProperties.isAmbienteTeste()) {
            throw new IllegalStateException(
                    "Configuração incompatível: modo TESTE exige MERCADO_PAGO_AMBIENTE_TESTE=true.");
        }
        if (modo == FluxoPlanoModo.PRODUCAO && mercadoPagoProperties.isAmbienteTeste()) {
            throw new IllegalStateException(
                    "Configuração incompatível: modo PRODUCAO exige MERCADO_PAGO_AMBIENTE_TESTE=false.");
        }
    }
}
