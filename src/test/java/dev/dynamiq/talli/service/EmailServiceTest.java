package dev.dynamiq.talli.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

    private HttpRequest captureRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
