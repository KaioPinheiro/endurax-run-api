package com.kaio.runtracker.controller;

import com.kaio.runtracker.config.FluxoPlanoProperties;
import com.kaio.runtracker.config.MercadoPagoProperties;
import com.kaio.runtracker.dto.ConfigPublicaResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/publica")
public class ConfigPublicaController {
    private final FluxoPlanoProperties fluxoPlanoProperties;
    private final MercadoPagoProperties mercadoPagoProperties;

    public ConfigPublicaController(
            FluxoPlanoProperties fluxoPlanoProperties,
            MercadoPagoProperties mercadoPagoProperties) {
        this.fluxoPlanoProperties = fluxoPlanoProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
    }

    @GetMapping
    public ConfigPublicaResponseDTO consultar() {
        return new ConfigPublicaResponseDTO(
                fluxoPlanoProperties.getModo(),
                fluxoPlanoProperties.isPagamentoObrigatorio(),
                mercadoPagoProperties.isAmbienteTeste(),
                mercadoPagoProperties.getValorPlano());
    }
}
