package com.kaio.runtracker.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Component
@Validated
@ConfigurationProperties(prefix = "mercado-pago")
public class MercadoPagoProperties {

    @NotBlank(message = "MERCADO_PAGO_ACCESS_TOKEN não foi configurado")
    private String accessToken;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal valorPlano;

    @Min(1)
    private int expiracaoPixMinutos;

    private boolean ambienteTeste = true;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public BigDecimal getValorPlano() { return valorPlano; }
    public void setValorPlano(BigDecimal valorPlano) { this.valorPlano = valorPlano; }
    public int getExpiracaoPixMinutos() { return expiracaoPixMinutos; }
    public void setExpiracaoPixMinutos(int expiracaoPixMinutos) { this.expiracaoPixMinutos = expiracaoPixMinutos; }
    public boolean isAmbienteTeste() { return ambienteTeste; }
    public void setAmbienteTeste(boolean ambienteTeste) { this.ambienteTeste = ambienteTeste; }
}
