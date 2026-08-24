package com.kaio.runtracker.security;

import com.kaio.runtracker.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MercadoPagoWebhookSignatureValidator {

    private final MercadoPagoProperties properties;

    public MercadoPagoWebhookSignatureValidator(MercadoPagoProperties properties) {
        this.properties = properties;
    }

    public boolean validar(String assinatura, String requestId, String dataId) {
        if (!StringUtils.hasText(assinatura)
                || !StringUtils.hasText(requestId)
                || !StringUtils.hasText(dataId)
                || !StringUtils.hasText(properties.getWebhookSecret())) {
            return false;
        }

        String timestamp = parte(assinatura, "ts");
        String hashRecebido = parte(assinatura, "v1");
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(hashRecebido)) {
            return false;
        }

        try {
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculado = hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] recebido = HexFormat.of().parseHex(hashRecebido);
            return MessageDigest.isEqual(calculado, recebido);
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
            throw new IllegalStateException("Não foi possível validar a assinatura do webhook.", exception);
        }
    }

    private String parte(String assinatura, String nome) {
        for (String parte : assinatura.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length == 2 && nome.equals(chaveValor[0].trim())) {
                return chaveValor[1].trim();
            }
        }
        return null;
    }
}
