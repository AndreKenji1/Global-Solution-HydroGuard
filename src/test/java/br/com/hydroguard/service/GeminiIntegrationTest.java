package br.com.hydroguard.service;

import br.com.hydroguard.model.Prioridade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpClient client;
    private HttpResponse<String> response;
    private TriagemService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        service = new TriagemService("chave-ficticia-de-teste", "gemini-2.5-flash", json,
            client, Duration.ofMillis(250));
    }

    private void resposta(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(response);
    }

    private String envelope(String prediction, String finishReason) throws Exception {
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of(
            "finishReason", finishReason, "content", Map.of("parts", List.of(Map.of("text", prediction)))))));
    }

    private void assertContingencia(TriagemService.Resultado result) {
        assertAll(
            () -> assertEquals(Prioridade.ALTA, result.prioridade()),
            () -> assertEquals("REGRAS_CONTINGENCIA", result.tipoAnalise()),
            () -> assertTrue(result.justificativa().startsWith("IA indisponível;")),
            () -> assertFalse(result.justificativa().contains("chave-ficticia-de-teste")));
    }

    @Test
    void classificaRespostaEstruturadaEEnviaSomenteTextoComChaveNoCabecalho() throws Exception {
        resposta(200, envelope("{\"prioridade\":\"ALTA\",\"categoria\":\"Rompimento\",\"justificativa\":\"Relato de fluxo intenso.\"}", "STOP"));

        var result = service.classificar("Cano rompido com água jorrando");

        assertAll(
            () -> assertEquals("IA_GEMINI", result.tipoAnalise()),
            () -> assertEquals(Prioridade.ALTA, result.prioridade()),
            () -> assertEquals("Rompimento", result.categoria()),
            () -> assertEquals("Relato de fluxo intenso.", result.justificativa()));
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest request = captor.getValue();
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", request.uri().toString());
        assertEquals("chave-ficticia-de-teste", request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertEquals(Duration.ofMillis(250), request.timeout().orElseThrow());
        JsonNode payload = json.readTree(readBody(request));
        assertEquals("Cano rompido com água jorrando", payload.at("/contents/0/parts/0/text").asText());
        assertTrue(payload.path("systemInstruction").isObject());
        assertEquals("application/json", payload.at("/generationConfig/responseMimeType").asText());
        assertEquals(3, payload.at("/generationConfig/responseSchema/required").size());
        assertFalse(payload.toString().contains("chave-ficticia-de-teste"));
        assertFalse(payload.toString().contains("inlineData"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-json", "null", "[]", "{}",
        "{\"prioridade\":\"URGENTE\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":123,\"justificativa\":\"Fluxo.\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":null,\"justificativa\":\"Fluxo.\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":\"  \",\"justificativa\":\"Fluxo.\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\",\"justificativa\":\"\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\",\"extra\":true}",
        "{\"prioridade\":\"ALTA\",\"prioridade\":\"BAIXA\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\"}",
        "{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\"} {}",
        "{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\\nurbana\",\"justificativa\":\"Fluxo.\"}"
    })
    void rejeitaClassificacoesInvalidasSemImpedirRegistro(String prediction) throws Exception {
        resposta(200, envelope(prediction, "STOP"));
        assertContingencia(service.classificar("Cano rompido na rua"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoria", "justificativa"})
    void rejeitaTextoExcessivoEmVezDeTruncar(String campo) throws Exception {
        String prediction = json.writeValueAsString(Map.of("prioridade", "ALTA",
            "categoria", campo.equals("categoria") ? "x".repeat(71) : "Rede",
            "justificativa", campo.equals("justificativa") ? "x".repeat(251) : "Fluxo intenso."));
        resposta(200, envelope(prediction, "STOP"));
        assertContingencia(service.classificar("Cano rompido na rua"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAX_TOKENS", "SAFETY", "RECITATION", ""})
    void naoAceitaRespostaTruncadaOuBloqueada(String finishReason) throws Exception {
        resposta(200, envelope("{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\"}", finishReason));
        assertContingencia(service.classificar("Cano rompido na rua"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"candidates\":[]}", "invalid-json"})
    void utilizaRegrasQuandoGeminiNaoRetornaCandidato(String body) throws Exception {
        resposta(200, body);
        assertContingencia(service.classificar("Cano rompido na rua"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void falhaDeAutenticacaoCotaOuServidorUsaContingenciaSemRepetirChamada(int status) throws Exception {
        resposta(status, "corpo externo não deve ser exposto ao usuário");
        assertContingencia(service.classificar("Cano rompido na rua"));
        verify(client, times(1)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429})
    void registraSomenteStatusHttpSemSegredoCorpoOuRelato(int status) throws Exception {
        String corpoPrivado = "corpo-externo-sensivel-nao-registrar";
        String relatoPrivado = "Cano rompido na rua; relato-privado-nao-registrar";
        resposta(status, corpoPrivado);
        Logger logger = (Logger) LoggerFactory.getLogger(TriagemService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertContingencia(service.classificar(relatoPrivado));
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().equals("Gemini HTTP " + status)));
            String log = String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
            assertFalse(log.contains("chave-ficticia-de-teste"));
            assertFalse(log.contains("x-goog-api-key"));
            assertFalse(log.contains(corpoPrivado));
            assertFalse(log.contains(relatoPrivado));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
            verify(response, never()).body();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void timeoutUsaRegras() throws Exception {
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new HttpTimeoutException("timeout simulado"));
        assertContingencia(service.classificar("Cano rompido na rua"));
    }

    @Test
    void interrupcaoPreservaSinalDaThread() throws Exception {
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new InterruptedException("interrupção simulada"));
        try {
            assertContingencia(service.classificar("Cano rompido na rua"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void semChaveNaoChamaRedeEIdentificaRegras() {
        var semChave = new TriagemService("", "gemini-2.5-flash", json, client, Duration.ofSeconds(1));
        assertEquals("REGRAS", semChave.classificar("Torneira pingando devagar").tipoAnalise());
        verifyNoInteractions(client);
    }

    @Test
    void rejeitaModeloComCaminhoOuUrl() {
        assertThrows(IllegalArgumentException.class, () ->
            new TriagemService("", "../outro:metodo?key=abc", json, client, Duration.ofSeconds(1)));
    }

    @Test
    void respostaExcessivaUsaRegras() throws Exception {
        resposta(200, "x".repeat(65537));
        assertContingencia(service.classificar("Cano rompido na rua"));
    }

    @Test
    void limiteSimultaneoUsaContingenciaSemEnfileirarChamadas() throws Exception {
        var limitado = new TriagemService("chave-de-teste", "gemini-2.5-flash", json,
            client, Duration.ofSeconds(1), 1);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(envelope("{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\"}", "STOP"));
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenAnswer(invocation -> {
                entrou.countDown();
                if (!liberar.await(5, TimeUnit.SECONDS)) throw new HttpTimeoutException("timeout do mock");
                return response;
            });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var primeira = executor.submit(() -> limitado.classificar("Cano rompido na rua"));
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            assertContingencia(limitado.classificar("Cano rompido na rua"));
            liberar.countDown();
            assertEquals("IA_GEMINI", primeira.get(5, TimeUnit.SECONDS).tipoAnalise());
            verify(client, times(1)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        } finally {
            liberar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void falhaLiberaVagaParaProximaClassificacao() throws Exception {
        var limitado = new TriagemService("chave-de-teste", "gemini-2.5-flash", json,
            client, Duration.ofSeconds(1), 1);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(envelope("{\"prioridade\":\"ALTA\",\"categoria\":\"Rede\",\"justificativa\":\"Fluxo.\"}", "STOP"));
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new HttpTimeoutException("timeout simulado")).thenReturn(response);
        assertContingencia(limitado.classificar("Cano rompido na rua"));
        assertEquals("IA_GEMINI", limitado.classificar("Cano rompido na rua").tipoAnalise());
    }

    private String readBody(HttpRequest request) throws Exception {
        CompletableFuture<String> body = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            @Override public void onError(Throwable error) { body.completeExceptionally(error); }
            @Override public void onComplete() { body.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return body.get(1, TimeUnit.SECONDS);
    }
}
