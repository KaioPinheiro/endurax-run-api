package com.kaio.runtracker.security;

import com.kaio.runtracker.config.IntervalsIcuProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntervalsIcuTokenCipherTest {
    @Test
    void cifraComAesGcmENaoPersisteTokenEmTextoClaro() {
        IntervalsIcuProperties properties = configuredProperties();
        IntervalsIcuTokenCipher cipher = new IntervalsIcuTokenCipher(properties);

        String encrypted = cipher.encrypt("access-token-secreto");

        assertNotEquals("access-token-secreto", encrypted);
        assertEquals("access-token-secreto", cipher.decrypt(encrypted));
    }

    @Test
    void rejeitaChaveQueNaoTenhaTrintaEDoisBytes() {
        IntervalsIcuProperties properties = configuredProperties();
        properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));

        assertThrows(IllegalStateException.class,
                () -> new IntervalsIcuTokenCipher(properties).encrypt("token"));
    }

    private IntervalsIcuProperties configuredProperties() {
        IntervalsIcuProperties properties = new IntervalsIcuProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://api.example.com/callback");
        properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }
}
