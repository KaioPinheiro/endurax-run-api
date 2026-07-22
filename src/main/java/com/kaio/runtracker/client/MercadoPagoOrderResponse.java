package com.kaio.runtracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoOrderResponse(
        String id,
        String status,
        @JsonProperty("status_detail") String statusDetail,
        @JsonProperty("external_reference") String externalReference,
        Transactions transactions) {

    public Payment primeiroPagamento() {
        if (transactions == null || transactions.payments() == null || transactions.payments().isEmpty()) {
            return null;
        }
        return transactions.payments().get(0);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transactions(List<Payment> payments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(
            String id,
            String status,
            @JsonProperty("status_detail") String statusDetail,
            @JsonProperty("date_of_expiration") String dateOfExpiration,
            @JsonProperty("payment_method") PaymentMethod paymentMethod) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentMethod(
            String id,
            String type,
            @JsonProperty("ticket_url") String ticketUrl,
            @JsonProperty("qr_code") String qrCode,
            @JsonProperty("qr_code_base64") String qrCodeBase64) {}
}
