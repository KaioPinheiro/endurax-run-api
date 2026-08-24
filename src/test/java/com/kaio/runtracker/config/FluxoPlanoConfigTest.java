package com.kaio.runtracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

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
        assertDoesNotThrow(() -> validator(fluxo, mercadoPago, "dev").validar());
    }

    @Test
    void producaoExigePagamentoEAmbienteReal() {
        FluxoPlanoProperties fluxo = fluxo(FluxoPlanoModo.PRODUCAO);
        MercadoPagoProperties mercadoPago = mercadoPago(false);

        assertTrue(fluxo.isPagamentoObrigatorio());
        assertDoesNotThrow(() -> validator(fluxo, mercadoPago, "prod").validar());
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
        FluxoPlanoConfigValidator validator = validator(
                fluxo(FluxoPlanoModo.TESTE), mercadoPago(false), "dev");

        assertThrows(IllegalStateException.class, validator::validar);
    }

    @Test
    void producaoComSandboxFalha() {
        FluxoPlanoConfigValidator validator = validator(
                fluxo(FluxoPlanoModo.PRODUCAO), mercadoPago(true), "prod");

        assertThrows(IllegalStateException.class, validator::validar);
    }

    @Test
    void prodComDesenvolvimentoFalha() {
        FluxoPlanoConfigValidator validator = validator(
                fluxo(FluxoPlanoModo.DESENVOLVIMENTO), mercadoPago(false), "prod");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, validator::validar);
        assertTrue(exception.getMessage().contains(
                "profile prod não permite APP_FLUXO_PLANO_MODO=DESENVOLVIMENTO"));
    }

    @Test
    void devComDesenvolvimentoPermitido() {
        FluxoPlanoConfigValidator validator = validator(
                fluxo(FluxoPlanoModo.DESENVOLVIMENTO), mercadoPago(true), "dev");

        assertDoesNotThrow(validator::validar);
    }

    private FluxoPlanoConfigValidator validator(
            FluxoPlanoProperties fluxo,
            MercadoPagoProperties mercadoPago,
            String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new FluxoPlanoConfigValidator(fluxo, mercadoPago, environment);
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
