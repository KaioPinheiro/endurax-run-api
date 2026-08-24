package com.kaio.runtracker.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaio.runtracker.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookSignatureValidatorTest {

    private static final String SECRET = "secret-configurado-no-painel";
    private final MercadoPagoWebhookSignatureValidator validator = validator();

    @Test
    void aceitaAssinaturaHmacSha256Valida() throws Exception {
        String dataId = "ORD123";
        String requestId = "request-abc";
        String timestamp = "1742505638683";
        String assinatura = "ts=" + timestamp + ",v1="
                + assinar("id:ORD123;request-id:request-abc;ts:1742505638683;");

        assertTrue(validator.validar(assinatura, requestId, dataId));
    }

    @Test
    void rejeitaAssinaturaInvalidaOuDadosObrigatoriosAusentes() {
        assertFalse(validator.validar("ts=1,v1=" + "0".repeat(64), "req", "ORD123"));
        assertFalse(validator.validar(null, "req", "ORD123"));
        assertFalse(validator.validar("ts=1,v1=abc", null, "ORD123"));
        assertFalse(validator.validar("ts=1,v1=abc", "req", null));
    }

    private MercadoPagoWebhookSignatureValidator validator() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setWebhookSecret(SECRET);
        return new MercadoPagoWebhookSignatureValidator(properties);
    }

    private String assinar(String manifest) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
