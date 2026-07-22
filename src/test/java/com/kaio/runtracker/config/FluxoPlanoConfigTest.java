package com.kaio.runtracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluxoPlanoConfigTest {

    @Test
    void desenvolvimentoNaoExigePagamento() {
        FluxoPlanoProperties properties = fluxo(FluxoPlanoModo.DESENVOLVIMENTO);

        assertFalse(properties.isPagamentoObrigatorio());
        assertTrue(properties.isGeracaoDiretaPermitida());
    }

    @Test
    void testeExigePagamentoESandbox() {
        FluxoPlanoProperties fluxo = fluxo(FluxoPlanoModo.TESTE);
        MercadoPagoProperties mercadoPago = mercadoPago(true);

        assertTrue(fluxo.isPagamentoObrigatorio());
        assertDoesNotThrow(() -> new FluxoPlanoConfigValidator(fluxo, mercadoPago).validar());
    }

    @Test
    void producaoExigePagamentoEAmbienteReal() {
        FluxoPlanoProperties fluxo = fluxo(FluxoPlanoModo.PRODUCAO);
        MercadoPagoProperties mercadoPago = mercadoPago(false);

        assertTrue(fluxo.isPagamentoObrigatorio());
        assertDoesNotThrow(() -> new FluxoPlanoConfigValidator(fluxo, mercadoPago).validar());
    }

    @Test
    void modoInvalidoFalhaNoBinding() {
        Binder binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.fluxo-plano.modo", "INVALIDO")));

        assertThrows(Exception.class,
                () -> binder.bind("app.fluxo-plano", Bindable.of(FluxoPlanoProperties.class)).get());
    }

    @Test
    void testeComMercadoPagoRealFalha() {
        FluxoPlanoConfigValidator validator = new FluxoPlanoConfigValidator(
                fluxo(FluxoPlanoModo.TESTE), mercadoPago(false));

        assertThrows(IllegalStateException.class, validator::validar);
    }

    @Test
    void producaoComSandboxFalha() {
        FluxoPlanoConfigValidator validator = new FluxoPlanoConfigValidator(
                fluxo(FluxoPlanoModo.PRODUCAO), mercadoPago(true));

        assertThrows(IllegalStateException.class, validator::validar);
    }

    private FluxoPlanoProperties fluxo(FluxoPlanoModo modo) {
        FluxoPlanoProperties properties = new FluxoPlanoProperties();
        properties.setModo(modo);
        return properties;
    }

    private MercadoPagoProperties mercadoPago(boolean ambienteTeste) {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAmbienteTeste(ambienteTeste);
        return properties;
    }
}
