package com.kaio.runtracker.security;

import com.kaio.runtracker.config.IntervalsIcuProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class IntervalsIcuTokenCipher {
    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte[] AAD = "endurax:intervals-icu:access-token:v1".getBytes(StandardCharsets.UTF_8);

    private final IntervalsIcuProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public IntervalsIcuTokenCipher(IntervalsIcuProperties properties) {
        this(properties, new SecureRandom());
    }

    IntervalsIcuTokenCipher(IntervalsIcuProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public String encrypt(String token) {
        properties.requireConfigured();
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Token vazio.");
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(1 + iv.length + encrypted.length)
                    .put(VERSION).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível proteger o token do Intervals.icu.", exception);
        }
    }

    public String decrypt(String encoded) {
        properties.requireConfigured();
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            if (payload.remaining() <= IV_LENGTH || payload.get() != VERSION) {
                throw new IllegalArgumentException("Token cifrado inválido.");
            }
            byte[] iv = new byte[IV_LENGTH];
            payload.get(iv);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Token cifrado do Intervals.icu inválido.", exception);
        }
    }

    private SecretKeySpec key() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getTokenEncryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("INTERVALS_ICU_TOKEN_ENCRYPTION_KEY deve estar em Base64.", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("INTERVALS_ICU_TOKEN_ENCRYPTION_KEY deve representar 32 bytes.");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
