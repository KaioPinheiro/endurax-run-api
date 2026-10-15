package com.kaio.runtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "intervals-icu.oauth")
public class IntervalsIcuProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String tokenEncryptionKey;
    private String authorizationUrl = "https://intervals.icu/oauth/authorize";
    private String tokenUrl = "https://intervals.icu/api/oauth/token";
    private String apiBaseUrl = "https://intervals.icu/api/v1";
    private int stateTtlMinutes = 10;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;

    public void requireConfigured() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)
                || !StringUtils.hasText(redirectUri) || !StringUtils.hasText(tokenEncryptionKey)) {
            throw new IllegalStateException("OAuth do Intervals.icu não está configurado.");
        }
        if (stateTtlMinutes < 1 || connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalStateException("Configuração OAuth do Intervals.icu inválida.");
        }
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getTokenEncryptionKey() { return tokenEncryptionKey; }
    public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = tokenEncryptionKey; }
    public String getAuthorizationUrl() { return authorizationUrl; }
    public void setAuthorizationUrl(String authorizationUrl) { this.authorizationUrl = authorizationUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public int getStateTtlMinutes() { return stateTtlMinutes; }
    public void setStateTtlMinutes(int stateTtlMinutes) { this.stateTtlMinutes = stateTtlMinutes; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
