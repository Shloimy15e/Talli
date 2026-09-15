package dev.dynamiq.talli.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;
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
    private EmailSenderProfileService senders;
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

        senders = mock(EmailSenderProfileService.class);
        when(senders.resolve(null)).thenReturn(new EmailSender("test@dynamiq.dev", "Test Sender", "<b>Test Sender</b>"));
        when(senders.resolve("billing@dynamiq.dev"))
                .thenReturn(new EmailSender("billing@dynamiq.dev", "Billing | Dynamiq Solutions",
                        "<strong>Current Billing signature</strong>"));
        service = new EmailService(templateEngine, senders);
        setField(service, "http", httpClient);
        setField(service, "apiKey", "re_test_key");
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
        verify(httpClient).send(argThat(request -> request.method().equals("POST")), any());
    }

    @Test
    void invoiceAndReminderTemplatesUseTheCurrentBillingSender() throws Exception {
        service.sendTemplate("invoice@example.com", "Invoice", "invoice", java.util.Map.of());
        service.sendTemplate("reminder@example.com", "Reminder", "reminder", java.util.Map.of());

        List<HttpRequest> requests = capturePostRequests();
        assertThat(requests).hasSize(2);
        for (HttpRequest request : requests) {
            JsonNode payload = new ObjectMapper().readTree(readBody(request));
            assertThat(payload.path("from").asText())
                    .isEqualTo("Billing | Dynamiq Solutions <billing@dynamiq.dev>");
        }

        ArgumentCaptor<Context> invoiceContext = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/invoice"), invoiceContext.capture());
        assertThat(invoiceContext.getValue().getVariable("signature"))
                .isEqualTo("<strong>Current Billing signature</strong>");

        ArgumentCaptor<Context> reminderContext = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/reminder"), reminderContext.capture());
        assertThat(reminderContext.getValue().getVariable("signature"))
                .isEqualTo("<strong>Current Billing signature</strong>");
        verify(senders, times(2)).resolve("billing@dynamiq.dev");
        verify(senders, never()).resolve(null);
    }

    @Test
    void inviteTemplateKeepsTheDefaultSender() throws Exception {
        service.sendTemplate("invitee@example.com", "Welcome", "invite", java.util.Map.of());

        JsonNode payload = new ObjectMapper().readTree(readBody(captureRequest()));
        assertThat(payload.path("from").asText()).isEqualTo("Test Sender <test@dynamiq.dev>");
        ArgumentCaptor<Context> context = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/invite"), context.capture());
        assertThat(context.getValue().getVariable("signature")).isEqualTo("<b>Test Sender</b>");
        verify(senders).resolve(null);
        verify(senders, never()).resolve("billing@dynamiq.dev");
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
    void sendPlain_usesExplicitSenderIdentity() throws Exception {
        service.sendPlain(new EmailSender("billing@dynamiq.dev", "Dynamiq Billing", ""),
                "to@example.com", List.of(), List.of(), "Hello", "body text");

        JsonNode payload = new ObjectMapper().readTree(readBody(captureRequest()));
        assertThat(payload.path("from").asText())
                .isEqualTo("Dynamiq Billing <billing@dynamiq.dev>");
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
        verify(httpClient, atLeastOnce()).send(captor.capture(), any());
        return captor.getAllValues().stream().filter(request -> request.method().equals("POST")).findFirst().orElseThrow();
    }

    private List<HttpRequest> capturePostRequests() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, atLeastOnce()).send(captor.capture(), any());
        return captor.getAllValues().stream().filter(request -> request.method().equals("POST")).toList();
    }

    @Test
    void repliesSendHeadersAndKeepProviderMessageId() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"message_id\":\"<actual@provider.test>\"}");
        doReturn(response).when(httpClient).send(argThat(request -> request.method().equals("GET")), any());
        var result = service.sendMessage(new EmailSender("billing@dynamiq.dev", "Billing", ""),
                "to@example.com", List.of(), List.of(), "Re: Hello", "Reply", null, List.of(),
                java.util.Map.of("In-Reply-To", "<parent@example.com>", "References", "<parent@example.com>"));
        JsonNode payload = new ObjectMapper().readTree(readBody(captureRequest()));
        assertThat(payload.path("headers").path("In-Reply-To").asText()).isEqualTo("<parent@example.com>");
        assertThat(payload.path("headers").path("References").asText()).isEqualTo("<parent@example.com>");
        assertThat(result.messageId()).isEqualTo("<actual@provider.test>");
        assertThat(result.fromAddress()).isEqualTo("billing@dynamiq.dev");
    }

    @Test
    void metadataLookupFailureDoesNotReportAcceptedEmailAsFailed() throws Exception {
        doThrow(new java.io.IOException("unavailable")).when(httpClient)
                .send(argThat(request -> request.method().equals("GET")), any());
        var result = service.sendPlain("to@example.com", "Hello", "Body");
        assertThat(result.resendId()).isEqualTo("msg_123");
        assertThat(result.messageId()).isNull();
    }

    @Test
    void receivedEmailReadsCaseInsensitiveThreadHeaders() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"message_id\":\"<incoming@example.com>\",\"headers\":{\"in-reply-to\":\"<parent@example.com>\",\"References\":\"<first@example.com> <parent@example.com>\"}}");
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        var result = service.fetchReceivedEmail("received-123");
        assertThat(result.messageId()).isEqualTo("<incoming@example.com>");
        assertThat(result.inReplyTo()).isEqualTo("<parent@example.com>");
        assertThat(result.references()).contains("<first@example.com>");
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
