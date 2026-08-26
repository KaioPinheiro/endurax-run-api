package com.kaio.runtracker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    @Min(100)
    private int maxEntries = 10_000;

    @Valid
    private Limit solicitacao = new Limit(10, 3_600);

    @Valid
    private Limit pix = new Limit(5, 900);

    @Valid
    private Limit geracao = new Limit(3, 3_600);

    @Valid
    private Limit consulta = new Limit(120, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public Limit getSolicitacao() {
        return solicitacao;
    }

    public void setSolicitacao(Limit solicitacao) {
        this.solicitacao = solicitacao;
    }

    public Limit getPix() {
        return pix;
    }

    public void setPix(Limit pix) {
        this.pix = pix;
    }

    public Limit getGeracao() {
        return geracao;
    }

    public void setGeracao(Limit geracao) {
        this.geracao = geracao;
    }

    public Limit getConsulta() {
        return consulta;
    }

    public void setConsulta(Limit consulta) {
        this.consulta = consulta;
    }

    public static class Limit {
        @Min(1)
        private int requests;

        @Min(1)
        private long windowSeconds;

        public Limit() {
        }

        public Limit(int requests, long windowSeconds) {
            this.requests = requests;
            this.windowSeconds = windowSeconds;
        }

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = requests;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
