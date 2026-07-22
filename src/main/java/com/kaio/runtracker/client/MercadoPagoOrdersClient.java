package com.kaio.runtracker.client;

import com.kaio.runtracker.config.MercadoPagoProperties;
import com.kaio.runtracker.exception.PagamentoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class MercadoPagoOrdersClient {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoOrdersClient.class);
    private static final String BASE_URL = "https://api.mercadopago.com";

    private final MercadoPagoProperties properties;
    private final RestClient restClient;

    @Autowired
    public MercadoPagoOrdersClient(MercadoPagoProperties properties) {
        this(properties, criarRestClient());
    }

    MercadoPagoOrdersClient(MercadoPagoProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public MercadoPagoOrderResponse criarOrderPix(
            String email,
            String externalReference,
            String idempotencyKey,
            BigDecimal valor) {
        Map<String, Object> pagamento = Map.of(
                "amount", valor.toPlainString(),
                "payment_method", Map.of("id", "pix", "type", "bank_transfer"),
                "expiration_time", "PT" + properties.getExpiracaoPixMinutos() + "M"
        );
        Map<String, Object> body = Map.of(
                "type", "online",
                "processing_mode", "automatic",
                "external_reference", externalReference,
                "total_amount", valor.toPlainString(),
                "description", "Plano de corrida Endurax Run",
                "payer", criarPagador(email),
                "transactions", Map.of(
                    "payments", List.of(pagamento)
                )
        );

        return executar("criação", () -> restClient.post()
                .uri("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.setBearerAuth(properties.getAccessToken());
                    headers.set("X-Idempotency-Key", idempotencyKey);
                })
                .body(body)
                .retrieve()
                .body(MercadoPagoOrderResponse.class));
    }

    Map<String, String> criarPagador(String emailReal) {
        if (properties.isAmbienteTeste()) {
            return Map.of(
                    "email", "test_user_br@testuser.com",
                    "first_name", "APRO"
            );
        }
        return Map.of("email", emailReal);
    }

    public MercadoPagoOrderResponse consultarOrder(String orderId) {
        return executar("consulta", () -> restClient.get()
                .uri("/v1/orders/{id}", orderId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.getAccessToken()))
                .retrieve()
                .body(MercadoPagoOrderResponse.class));
    }

    private MercadoPagoOrderResponse executar(String operacao, Chamada chamada) {
        long inicio = System.nanoTime();
        try {
            MercadoPagoOrderResponse response = chamada.executar();
            if (response == null) {
                throw new PagamentoException(BAD_GATEWAY, "O Mercado Pago retornou uma resposta vazia.");
            }
            logger.info("Mercado Pago Orders: operacao={}, tempoMs={}, status={}, orderId={}",
                    operacao, tempoMs(inicio), response.status(), response.id());
            return response;
        } catch (PagamentoException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            logger.warn("Mercado Pago Orders recusou requisição: operacao={}, httpStatus={}, tempoMs={}",
                    operacao, exception.getStatusCode().value(), tempoMs(inicio));
            throw new PagamentoException(BAD_GATEWAY, "O Mercado Pago não conseguiu processar a solicitação.", exception);
        } catch (RestClientException exception) {
            logger.warn("Falha de comunicação com Mercado Pago: operacao={}, tempoMs={}, erro={}",
                    operacao, tempoMs(inicio), resumo(exception.getMessage()));
            throw new PagamentoException(SERVICE_UNAVAILABLE, "O serviço de pagamentos está temporariamente indisponível.", exception);
        }
    }

    private static RestClient criarRestClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    private long tempoMs(long inicio) { return (System.nanoTime() - inicio) / 1_000_000; }
    private String resumo(String valor) {
        if (valor == null || valor.isBlank()) return "<vazio>";
        String umaLinha = valor.replaceAll("[\\r\\n]+", " ");
        return umaLinha.length() > 500 ? umaLinha.substring(0, 500) + "..." : umaLinha;
    }

    @FunctionalInterface
    private interface Chamada { MercadoPagoOrderResponse executar(); }
}
