package dev.dynamiq.talli.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private HttpClient httpClient;
    private SpringTemplateEngine templateEngine;
    private EmailService service;

    @BeforeEach
    void setUp() throws Exception {
        httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"msg_123\"}");
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        templateEngine = mock(SpringTemplateEngine.class);
        when(templateEngine.process(anyString(), any())).thenReturn("<p>Rendered HTML</p>");

        service = new EmailService(templateEngine);
        setField(service, "http", httpClient);
        setField(service, "apiKey", "re_test_key");
        setField(service, "fromAddress", "test@dynamiq.dev");
        setField(service, "fromName", "Test Sender");
    }

    @Test
    void sendPlain_postsToResendWithCorrectFields() throws Exception {
        service.sendPlain("to@example.com", "Hello", "body text");

        HttpRequest sent = captureRequest();
        assertThat(sent.uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer re_test_key");
        assertThat(sent.method()).isEqualTo("POST");
    }

    @Test
    void sendTemplate_rendersTemplateAndPosts() throws Exception {
        service.sendTemplate("to@example.com", "Subject", "invoice", java.util.Map.of("name", "Shloimy"));

        verify(templateEngine).process(eq("emails/invoice"), any());
        verify(httpClient).send(any(HttpRequest.class), any());
    }

    @Test
    void sendPlain_postsCcAndBccToResend() throws Exception {
        service.sendPlain("to@example.com", List.of("cc@example.com"),
                List.of("bcc@example.com"), "Hello", "body text");

        JsonNode payload = new ObjectMapper().readTree(readBody(captureRequest()));
        assertThat(payload.path("to").get(0).asText()).isEqualTo("to@example.com");
        assertThat(payload.path("cc").get(0).asText()).isEqualTo("cc@example.com");
        assertThat(payload.path("bcc").get(0).asText()).isEqualTo("bcc@example.com");
    }

    @Test
    void sendHtml_encodesAttachmentsInResendPayload() throws Exception {
        List<EmailService.Attachment> attachments = List.of(
                new EmailService.Attachment("report.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain"),
                new EmailService.Attachment("data.bin", new byte[] {1, 2, 3}, null));

        service.sendHtml("to@example.com", List.of(), "Files", "See attached", "<p>See attached</p>", attachments);

        JsonNode payload = new ObjectMapper().readTree(readBody(captureRequest()));
        assertThat(payload.path("attachments")).hasSize(2);
        assertThat(payload.path("attachments").get(0).path("filename").asText()).isEqualTo("report.txt");
        assertThat(payload.path("attachments").get(0).path("content").asText())
                .isEqualTo(Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)));
        assertThat(payload.path("attachments").get(0).path("content_type").asText()).isEqualTo("text/plain");
        assertThat(payload.path("attachments").get(1).has("content_type")).isFalse();
    }

    private HttpRequest captureRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }

    private static String readBody(HttpRequest request) throws Exception {
        BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        CompletableFuture<byte[]> body = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(bytes.toByteArray());
            }
        });

        return new String(body.get(), StandardCharsets.UTF_8);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
