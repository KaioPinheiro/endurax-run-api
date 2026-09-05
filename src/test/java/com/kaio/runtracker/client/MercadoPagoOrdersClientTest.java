package com.kaio.runtracker.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kaio.runtracker.config.MercadoPagoProperties;
import com.kaio.runtracker.exception.PagamentoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class MercadoPagoOrdersClientTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoOrdersClient.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capturarLogs() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void limparLogs() {
        logger.detachAppender(appender);
    }

    @Test
    void usaPagadorDeTesteQuandoAmbienteTesteEstaAtivo() {
        MercadoPagoProperties properties = properties(true);
        MercadoPagoOrdersClient client = new MercadoPagoOrdersClient(properties, RestClient.create());

        Map<String, String> payer = client.criarPagador("cliente@email.com");

        assertEquals("test_user_br@testuser.com", payer.get("email"));
        assertEquals("APRO", payer.get("first_name"));
    }

    @Test
    void usaSomenteEmailRealQuandoAmbienteTesteEstaDesativado() {
        MercadoPagoProperties properties = properties(false);
        MercadoPagoOrdersClient client = new MercadoPagoOrdersClient(properties, RestClient.create());

        Map<String, String> payer = client.criarPagador("cliente@email.com");

        assertEquals("cliente@email.com", payer.get("email"));
        assertFalse(payer.containsKey("first_name"));
        assertFalse(payer.containsValue("test_user_br@testuser.com"));
        assertFalse(payer.containsValue("APRO"));
    }

    @Test
    void enviaExpiracaoRealDeQuinzeMinutosNaOrderPix() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.mercadopago.com/v1/orders"))
                .andExpect(method(POST))
                .andExpect(content().string(containsString("\"expiration_time\":\"PT15M\"")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("{\"id\":\"ORD123\",\"status\":\"pending\"}",
                                MediaType.APPLICATION_JSON));
        MercadoPagoProperties properties = properties(false);
        properties.setAccessToken("ACCESS_TOKEN");
        properties.setExpiracaoPixMinutos(15);
        MercadoPagoOrdersClient client = new MercadoPagoOrdersClient(properties, builder.build());

        client.criarOrderPix("cliente@email.com", "referencia", "idempotencia",
                new BigDecimal("12.90"));

        server.verify();
    }

    @Test
    void cancelaOrderComIdempotenciaPropria() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.mercadopago.com/v1/orders/ORD123/cancel"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer ACCESS_TOKEN"))
                .andExpect(header("X-Idempotency-Key", "cancel-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("{\"id\":\"ORD123\",\"status\":\"canceled\"}",
                                MediaType.APPLICATION_JSON));
        MercadoPagoProperties properties = properties(false);
        properties.setAccessToken("ACCESS_TOKEN");
        MercadoPagoOrdersClient client = new MercadoPagoOrdersClient(properties, builder.build());

        MercadoPagoOrderResponse response = client.cancelarOrder("ORD123", "cancel-key");

        assertEquals("canceled", response.status());
        server.verify();
    }

    @Test
    void registraSomenteDiagnosticoSanitizadoDoErroConhecidoEPreservaBadGateway() {
        String email = "cliente@example.com";
        String externalReference = "550e8400-e29b-41d4-a716-446655440000";
        String accessToken = "APP_USR-12345678901234567890";
        String resposta = """
                {
                  "error":"invalid_email_for_sandbox",
                  "message":"Email cliente@example.com inválido; referência 550e8400-e29b-41d4-a716-446655440000",
                  "cause":[{"code":"invalid_payer","field":"payer.email"}],
                  "details":[{"code":"required_properties","property":"transactions.payments"}],
                  "access_token":"APP_USR-12345678901234567890",
                  "qr_data":"pix-sensivel-123"
                }
                """;
        ClienteMock mock = clienteComResposta(BAD_REQUEST, resposta, accessToken);

        assertThatThrownBy(() -> mock.client().criarOrderPix(email, externalReference,
                        "38fd39a0-09b4-4d2d-ae82-65e6124bc035", new BigDecimal("12.90")))
                .isInstanceOfSatisfying(PagamentoException.class,
                        erro -> assertThat(erro.getStatus()).isEqualTo(BAD_GATEWAY));

        mock.server().verify();
        String log = logCompleto();
        assertThat(log).contains(
                "mpErrorCode=invalid_email_for_sandbox",
                "mpMessage=Email [REDACTED] inválido; referência [REDACTED]",
                "causeCodes=[invalid_payer, required_properties]",
                "invalidFields=[payer.email, transactions.payments]",
                "mpErrorDetails=available");
        assertThat(log).doesNotContain(email, externalReference, accessToken, "pix-sensivel-123", resposta);
    }

    @Test
    void naoRegistraBodyQuandoJsonNaoForReconhecido() {
        String resposta = "<html>erro interno com segredo-nao-logar</html>";
        ClienteMock mock = clienteComResposta(BAD_REQUEST, resposta, "token-teste");

        assertThatThrownBy(() -> mock.client().criarOrderPix("cliente@example.com", "referencia",
                        "idempotencia", new BigDecimal("12.90")))
                .isInstanceOf(PagamentoException.class);

        mock.server().verify();
        assertThat(logCompleto())
                .contains("jsonParseable=false", "jsonType=non-json", "mpErrorDetails=unavailable")
                .doesNotContain(resposta, "segredo-nao-logar");
    }

    @Test
    void registraSomenteChavesSegurasDeJsonObjetoDesconhecido() {
        String segredo = "cliente-secreto@example.com";
        String resposta = "{\"status\":400,\"opaque\":\"" + segredo
                + "\",\"chave estranha\":\"nao-logar\"}";
        ClienteMock mock = clienteComResposta(BAD_REQUEST, resposta, "token-teste",
                MediaType.parseMediaType("application/json;charset=UTF-8"));

        assertThatThrownBy(() -> criarOrder(mock)).isInstanceOf(PagamentoException.class);

        mock.server().verify();
        assertThat(logCompleto())
                .contains("contentType=application/json", "charset=UTF-8", "bodyEmpty=false",
                        "jsonParseable=true", "jsonType=object", "topLevelKeys=[status, opaque]")
                .doesNotContain(segredo, "nao-logar", "chave estranha");
    }

    @Test
    void registraSomenteTipoETamanhoDeJsonArray() {
        String resposta = "[\"segredo-um\",{\"email\":\"cliente@example.com\"},42]";
        ClienteMock mock = clienteComResposta(BAD_REQUEST, resposta, "token-teste");

        assertThatThrownBy(() -> criarOrder(mock)).isInstanceOf(PagamentoException.class);

        mock.server().verify();
        assertThat(logCompleto())
                .contains("jsonParseable=true", "jsonType=array", "jsonElementCount=3", "topLevelKeys=[]")
                .doesNotContain("segredo-um", "cliente@example.com");
    }

    @Test
    void registraBodyVazioSemTentarExporConteudo() {
        ClienteMock mock = clienteComResposta(BAD_REQUEST, "", "token-teste");

        assertThatThrownBy(() -> criarOrder(mock)).isInstanceOfSatisfying(PagamentoException.class,
                erro -> assertThat(erro.getStatus()).isEqualTo(BAD_GATEWAY));

        mock.server().verify();
        assertThat(logCompleto()).contains(
                "bodyLengthChars=0", "bodyLengthBytes=0", "bodyEmpty=true", "jsonType=empty");
    }

    @Test
    void extraiArrayErrorsComMultiplosErrosCamposEMensagemSanitizada() {
        String email = "cliente-secreto@example.com";
        String externalReference = "550e8400-e29b-41d4-a716-446655440000";
        String resposta = """
                {
                  "errors": [
                    {
                      "code": "invalid_email_for_sandbox",
                      "message": "Email cliente-secreto@example.com na referência 550e8400-e29b-41d4-a716-446655440000",
                      "field": "payer.email"
                    },
                    {
                      "error_code": "invalid_total_amount",
                      "property": "total_amount",
                      "details": [{"code": "amount_mismatch", "parameter": "transactions.payments"}]
                    }
                  ],
                  "conteudo_desconhecido": "valor-secreto-nao-logar"
                }
                """;
        ClienteMock mock = clienteComResposta(BAD_REQUEST, resposta, "token-teste");

        assertThatThrownBy(() -> criarOrder(mock)).isInstanceOfSatisfying(PagamentoException.class,
                erro -> assertThat(erro.getStatus()).isEqualTo(BAD_GATEWAY));

        mock.server().verify();
        assertThat(logCompleto())
                .contains(
                        "mpErrorCode=invalid_email_for_sandbox",
                        "mpMessage=Email [REDACTED] na referência [REDACTED]",
                        "causeCodes=[invalid_email_for_sandbox, invalid_total_amount, amount_mismatch]",
                        "invalidFields=[payer.email, total_amount, transactions.payments]",
                        "mpErrorDetails=available")
                .doesNotContain(email, externalReference, "valor-secreto-nao-logar", resposta);
    }

    @Test
    void ignoraErrorsEmFormatoInesperadoSemRegistrarValores() {
        String resposta = "{\"errors\":{\"code\":\"nao-deve-ser-extraido\",\"message\":\"segredo-nao-logar\"}}";
        ClienteMock mock = clienteComResposta(BAD_REQUEST, resposta, "token-teste");

        assertThatThrownBy(() -> criarOrder(mock)).isInstanceOf(PagamentoException.class);

        mock.server().verify();
        assertThat(logCompleto())
                .contains("topLevelKeys=[errors]", "mpErrorCode=unavailable",
                        "mpMessage=unavailable", "mpErrorDetails=unavailable")
                .doesNotContain("nao-deve-ser-extraido", "segredo-nao-logar", resposta);
    }

    private void criarOrder(ClienteMock mock) {
        mock.client().criarOrderPix("cliente@example.com", "referencia", "idempotencia",
                new BigDecimal("12.90"));
    }

    private ClienteMock clienteComResposta(org.springframework.http.HttpStatus status, String resposta, String token) {
        return clienteComResposta(status, resposta, token, MediaType.APPLICATION_JSON);
    }

    private ClienteMock clienteComResposta(
            org.springframework.http.HttpStatus status,
            String resposta,
            String token,
            MediaType contentType) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.mercadopago.com/v1/orders"))
                .andExpect(method(POST))
                .andRespond(withStatus(status).contentType(contentType).body(resposta));
        MercadoPagoProperties properties = properties(false);
        properties.setAccessToken(token);
        properties.setExpiracaoPixMinutos(15);
        return new ClienteMock(new MercadoPagoOrdersClient(properties, builder.build()), server);
    }

    private String logCompleto() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (acumulado, mensagem) -> acumulado + mensagem);
    }

    private MercadoPagoProperties properties(boolean ambienteTeste) {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAmbienteTeste(ambienteTeste);
        return properties;
    }

    private record ClienteMock(MercadoPagoOrdersClient client, MockRestServiceServer server) {}
}
