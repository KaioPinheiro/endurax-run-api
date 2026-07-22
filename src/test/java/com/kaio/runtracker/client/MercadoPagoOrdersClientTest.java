package com.kaio.runtracker.client;

import com.kaio.runtracker.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MercadoPagoOrdersClientTest {

    @Test
    void usaPagadorDeTesteQuandoAmbienteTesteEstaAtivo() {
        MercadoPagoProperties properties = properties(true);
        MercadoPagoOrdersClient client = new MercadoPagoOrdersClient(properties, RestClient.create());

        Map<String, String> payer = client.criarPagador("cliente@email.com");

        assertEquals("test_user_br@testuser.com", payer.get("email"));
        assertEquals("APRO", payer.get("first_name"));
    }

    @Test
    void usaSomenteEmailRealQuandoAmbienteTesteEstaDesativado() {
        MercadoPagoProperties properties = properties(false);
        MercadoPagoOrdersClient client = new MercadoPagoOrdersClient(properties, RestClient.create());

        Map<String, String> payer = client.criarPagador("cliente@email.com");

        assertEquals("cliente@email.com", payer.get("email"));
        assertFalse(payer.containsKey("first_name"));
        assertFalse(payer.containsValue("test_user_br@testuser.com"));
        assertFalse(payer.containsValue("APRO"));
    }

    private MercadoPagoProperties properties(boolean ambienteTeste) {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAmbienteTeste(ambienteTeste);
        return properties;
    }
}
