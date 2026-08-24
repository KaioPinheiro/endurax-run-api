package com.kaio.runtracker.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class FluxoPlanoConfigValidator {
    private final FluxoPlanoProperties fluxoPlanoProperties;
    private final MercadoPagoProperties mercadoPagoProperties;
    private final Environment environment;

    public FluxoPlanoConfigValidator(
            FluxoPlanoProperties fluxoPlanoProperties,
            MercadoPagoProperties mercadoPagoProperties,
            Environment environment) {
        this.fluxoPlanoProperties = fluxoPlanoProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void validar() {
        FluxoPlanoModo modo = fluxoPlanoProperties.getModo();
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && modo == FluxoPlanoModo.DESENVOLVIMENTO) {
            throw new IllegalStateException(
                    "Configuração incompatível: profile prod não permite APP_FLUXO_PLANO_MODO=DESENVOLVIMENTO.");
        }
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
