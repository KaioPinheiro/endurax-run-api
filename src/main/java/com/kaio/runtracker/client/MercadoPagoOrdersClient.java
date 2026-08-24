package com.kaio.runtracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class MercadoPagoOrdersClient {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoOrdersClient.class);
    private static final String BASE_URL = "https://api.mercadopago.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern UUID = Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final Pattern CREDENCIAL = Pattern.compile("(?i)\\b(?:bearer\\s+)?(?:APP_USR|TEST|sk)[-_][A-Za-z0-9._-]{12,}\\b");
    private static final Pattern VALOR_SEGURO = Pattern.compile("[A-Za-z][A-Za-z0-9_.\\[\\]-]{0,99}");

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
            DiagnosticoErro diagnostico = extrairDiagnostico(exception.getResponseBodyAsString());
            MetadadosResposta metadados = extrairMetadados(exception);
            logger.warn("Mercado Pago Orders recusou requisição: operacao={}, httpStatus={}, tempoMs={}, "
                            + "contentType={}, charset={}, bodyLengthChars={}, bodyLengthBytes={}, bodyEmpty={}, "
                            + "jsonParseable={}, jsonType={}, jsonElementCount={}, topLevelKeys={}, "
                            + "mpErrorCode={}, mpMessage={}, causeCodes={}, invalidFields={}, mpErrorDetails={}",
                    operacao, exception.getStatusCode().value(), tempoMs(inicio), metadados.contentType(),
                    metadados.charset(), metadados.tamanhoCaracteres(), metadados.tamanhoBytes(),
                    metadados.bodyVazio(), metadados.jsonParseavel(), metadados.tipoJson(),
                    metadados.quantidadeElementos(), metadados.chavesPrimeiroNivel(), diagnostico.codigo(),
                    diagnostico.mensagem(), diagnostico.codigosCausa(), diagnostico.camposInvalidos(),
                    diagnostico.disponivel() ? "available" : "unavailable");
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

    private DiagnosticoErro extrairDiagnostico(String body) {
        if (body == null || body.isBlank()) return DiagnosticoErro.indisponivel();
        try {
            JsonNode raiz = OBJECT_MAPPER.readTree(body);
            if (!raiz.isObject()) return DiagnosticoErro.indisponivel();

            String codigo = sanitizarValorEstruturado(primeiroTexto(raiz, "code", "error", "error_code"));
            String mensagem = sanitizarMensagem(primeiroTexto(raiz, "message"));
            Set<String> codigosCausa = new LinkedHashSet<>();
            Set<String> camposInvalidos = new LinkedHashSet<>();
            coletarDetalhes(raiz.get("cause"), codigosCausa, camposInvalidos);
            coletarDetalhes(raiz.get("causes"), codigosCausa, camposInvalidos);
            coletarDetalhes(raiz.get("details"), codigosCausa, camposInvalidos);

            boolean disponivel = codigo != null || mensagem != null
                    || !codigosCausa.isEmpty() || !camposInvalidos.isEmpty();
            return disponivel
                    ? new DiagnosticoErro(valorOuIndisponivel(codigo), valorOuIndisponivel(mensagem),
                            List.copyOf(codigosCausa), List.copyOf(camposInvalidos), true)
                    : DiagnosticoErro.indisponivel();
        } catch (Exception ignorada) {
            return DiagnosticoErro.indisponivel();
        }
    }

    private MetadadosResposta extrairMetadados(RestClientResponseException exception) {
        byte[] bytes = exception.getResponseBodyAsByteArray();
        String body = exception.getResponseBodyAsString();
        boolean vazio = bytes.length == 0;
        String contentType = "unavailable";
        String charset = "unavailable";
        if (exception.getResponseHeaders() != null && exception.getResponseHeaders().getContentType() != null) {
            MediaType mediaType = exception.getResponseHeaders().getContentType();
            contentType = mediaType.getType() + "/" + mediaType.getSubtype();
            Charset charsetResposta = mediaType.getCharset();
            if (charsetResposta != null) charset = charsetResposta.name();
        }
        if (vazio) {
            return new MetadadosResposta(contentType, charset, 0, 0, true,
                    false, "empty", 0, List.of());
        }

        try {
            JsonNode raiz = OBJECT_MAPPER.readTree(body);
            if (raiz == null) {
                return new MetadadosResposta(contentType, charset, body.length(), bytes.length, false,
                        true, "null", 0, List.of());
            }
            if (raiz.isObject()) {
                List<String> chaves = new java.util.ArrayList<>();
                raiz.fieldNames().forEachRemaining(chave -> {
                    if (chaves.size() < 20 && VALOR_SEGURO.matcher(chave).matches()) chaves.add(chave);
                });
                return new MetadadosResposta(contentType, charset, body.length(), bytes.length, false,
                        true, "object", 0, List.copyOf(chaves));
            }
            if (raiz.isArray()) {
                return new MetadadosResposta(contentType, charset, body.length(), bytes.length, false,
                        true, "array", raiz.size(), List.of());
            }
            return new MetadadosResposta(contentType, charset, body.length(), bytes.length, false,
                    true, "scalar", 0, List.of());
        } catch (Exception ignorada) {
            return new MetadadosResposta(contentType, charset, body.length(), bytes.length, false,
                    false, "non-json", 0, List.of());
        }
    }

    private void coletarDetalhes(JsonNode no, Set<String> codigos, Set<String> campos) {
        if (no == null || no.isNull()) return;
        if (no.isArray()) {
            no.forEach(item -> coletarDetalhes(item, codigos, campos));
            return;
        }
        if (!no.isObject()) return;

        adicionarSeSeguro(codigos, primeiroTexto(no, "code", "error", "error_code"));
        adicionarSeSeguro(campos, primeiroTexto(no, "field", "property", "parameter"));
    }

    private void adicionarSeSeguro(Set<String> destino, String valor) {
        String seguro = sanitizarValorEstruturado(valor);
        if (seguro != null && destino.size() < 10) destino.add(seguro);
    }

    private String primeiroTexto(JsonNode no, String... nomes) {
        for (String nome : nomes) {
            JsonNode valor = no.get(nome);
            if (valor != null && valor.isValueNode() && !valor.isNull()) return valor.asText();
        }
        return null;
    }

    private String sanitizarValorEstruturado(String valor) {
        if (valor == null || !VALOR_SEGURO.matcher(valor).matches()) return null;
        return valor;
    }

    private String sanitizarMensagem(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String seguro = valor.replaceAll("[\\r\\n\\t]+", " ");
        seguro = EMAIL.matcher(seguro).replaceAll("[REDACTED]");
        seguro = UUID.matcher(seguro).replaceAll("[REDACTED]");
        seguro = CREDENCIAL.matcher(seguro).replaceAll("[REDACTED]");
        seguro = seguro.replaceAll("[\\p{Cntrl}]", "").trim();
        return seguro.length() > 300 ? seguro.substring(0, 300) + "..." : seguro;
    }

    private String valorOuIndisponivel(String valor) {
        return valor == null ? "unavailable" : valor;
    }
    private String resumo(String valor) {
        if (valor == null || valor.isBlank()) return "<vazio>";
        String umaLinha = valor.replaceAll("[\\r\\n]+", " ");
        return umaLinha.length() > 500 ? umaLinha.substring(0, 500) + "..." : umaLinha;
    }

    @FunctionalInterface
    private interface Chamada { MercadoPagoOrderResponse executar(); }

    private record DiagnosticoErro(
            String codigo,
            String mensagem,
            List<String> codigosCausa,
            List<String> camposInvalidos,
            boolean disponivel) {
        private static DiagnosticoErro indisponivel() {
            return new DiagnosticoErro("unavailable", "unavailable", List.of(), List.of(), false);
        }
    }

    private record MetadadosResposta(
            String contentType,
            String charset,
            int tamanhoCaracteres,
            int tamanhoBytes,
            boolean bodyVazio,
            boolean jsonParseavel,
            String tipoJson,
            int quantidadeElementos,
            List<String> chavesPrimeiroNivel) {}
}
