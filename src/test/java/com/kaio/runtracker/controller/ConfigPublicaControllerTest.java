package com.kaio.runtracker.controller;

import com.kaio.runtracker.config.FluxoPlanoModo;
import com.kaio.runtracker.config.FluxoPlanoProperties;
import com.kaio.runtracker.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPublicaControllerTest {

    @Test
    void retornaSomenteConfiguracaoPublicaPermitida() {
        FluxoPlanoProperties fluxo = new FluxoPlanoProperties();
        fluxo.setModo(FluxoPlanoModo.TESTE);
        MercadoPagoProperties mercadoPago = new MercadoPagoProperties();
        mercadoPago.setAmbienteTeste(true);
        mercadoPago.setValorPlano(new BigDecimal("12.90"));
        mercadoPago.setAccessToken("NAO_DEVE_SER_EXPOSTO");

        var response = new ConfigPublicaController(fluxo, mercadoPago).consultar();

        assertEquals(FluxoPlanoModo.TESTE, response.modoFluxoPlano());
        assertTrue(response.pagamentoObrigatorio());
        assertTrue(response.ambientePagamentoTeste());
        assertEquals(new BigDecimal("12.90"), response.valorPlano());
        assertFalse(response.toString().contains("NAO_DEVE_SER_EXPOSTO"));
    }
}
