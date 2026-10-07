package dev.dynamiq.talli.mcp.events;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpCallbackTransportTest {
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(new byte[32]);
    @Test
    void rejectsNonPublicIpv4Ipv6AndCallbackUrlTricks() throws Exception {
        for (String ip : List.of("0.0.0.0", "10.1.2.3", "100.64.0.1", "127.0.0.1", "169.254.169.254", "172.31.0.1",
                "192.168.1.1", "192.0.0.8", "192.0.2.1", "192.88.99.2", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1",
                "::", "::1", "fe80::1", "fd00::1", "2001:db8::1", "2002:7f00:1::", "::ffff:127.0.0.1"))
            assertThat(McpCallbackTransport.isPublic(InetAddress.getByName(ip))).as(ip).isFalse();
        assertThat(McpCallbackTransport.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(McpCallbackTransport.isPublic(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
        for (String url : List.of("http://example.com", "https://user:secret@example.com", "https://example.com/#fragment",
                "https://example.com:0/", "https://example.com/\r\nInjected: yes", "https://example.com:65536/"))
            assertThatThrownBy(() -> McpCallbackTransport.validateUrl(url)).isInstanceOf(McpEventException.class);
    }
    @Test
    void revalidatesAllDnsAnswersBeforeConnectingAndRejectsRebinding() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        Socket socket = mock(Socket.class);
        McpCallbackTransport transport = new McpCallbackTransport(host -> {
            lookups.incrementAndGet();
            return new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")};
        }, factory, () -> socket);
        for (int attempt = 0; attempt < 2; attempt++)
            assertThatThrownBy(() -> transport.post("https://callback.example/path", "sub_test", "evt_test", new byte[0], SECRET, null))
                    .isInstanceOf(McpEventException.class).extracting("reason").isEqualTo("unsafe_address");
        assertThat(lookups).hasValue(2);
        verifyNoInteractions(socket, factory);
    }
    @Test
    void pinsValidatedAddressPreservesTlsHostSignsExactBytesAndDoesNotFollowRedirects() throws Exception {
        InetAddress first = InetAddress.getByName("8.8.8.8"), second = InetAddress.getByName("1.1.1.1");
        Socket failed = mock(Socket.class), connected = mock(Socket.class);
        doThrow(new ConnectException("unreachable")).when(failed).connect(any(), anyInt());
        AtomicInteger created = new AtomicInteger();
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        SSLSocket tls = mock(SSLSocket.class);
        when(factory.createSocket(connected, "callback.example", 443, true)).thenReturn(tls);
        when(tls.getSSLParameters()).thenReturn(new SSLParameters());
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        when(tls.getOutputStream()).thenReturn(sent);
        when(tls.getInputStream()).thenReturn(new ByteArrayInputStream("HTTP/1.1 302 Found\r\nLocation: https://127.0.0.1/\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
        McpCallbackTransport transport = new McpCallbackTransport(host -> new InetAddress[]{first, second}, factory,
                () -> created.getAndIncrement() == 0 ? failed : connected);
        byte[] body = "{\"subject\":\"שלום 🌍\"}".getBytes(StandardCharsets.UTF_8);
        String old = "whsec_" + Base64.getEncoder().encodeToString(new byte[24]);
        assertThat(transport.post("https://callback.example/events?q=1", "sub_test", "evt_test", body, SECRET, old).status()).isEqualTo(302);
        verify(failed).connect(eq(new InetSocketAddress(first, 443)), intThat(timeout -> timeout > 0 && timeout <= 2000));
        verify(failed).close();
        verify(connected).connect(eq(new InetSocketAddress(second, 443)), intThat(timeout -> timeout > 0 && timeout <= 2000));
        verify(factory).createSocket(connected, "callback.example", 443, true);
        ArgumentCaptor<SSLParameters> parameters = ArgumentCaptor.forClass(SSLParameters.class);
        verify(tls).setSSLParameters(parameters.capture());
        assertThat(parameters.getValue().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        assertThat(((SNIHostName) parameters.getValue().getServerNames().getFirst()).getAsciiName()).isEqualTo("callback.example");
        String request = sent.toString(StandardCharsets.UTF_8);
        assertThat(request).startsWith("POST /events?q=1 HTTP/1.1\r\nHost: callback.example\r\n");
        String timestamp = Arrays.stream(request.split("\r\n")).filter(v -> v.startsWith("webhook-timestamp: ")).findFirst().orElseThrow().substring(19);
        assertThat(request).contains("webhook-signature: " + StandardWebhookSigner.sign(SECRET, "evt_test", Long.parseLong(timestamp), body)
                + " " + StandardWebhookSigner.sign(old, "evt_test", Long.parseLong(timestamp), body));
        assertThat(sent.toByteArray()).endsWith(body);
        assertThat(created).hasValue(2);
    }
    @Test
    void boundsResponseAndUnderstandsChunkedVerification() throws Exception {
        var input = new ByteArrayInputStream("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        assertThat(McpCallbackTransport.readResponse(input).body()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
        var oversized = new ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 262145\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> McpCallbackTransport.readResponse(oversized)).isInstanceOf(IOException.class);
        var partial = new ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nx".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> McpCallbackTransport.readResponse(partial)).isInstanceOf(EOFException.class);
    }
    @Test
    void dedicatedSchedulerKeepsTheConventionalSchedulerAvailable() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(McpEventSchedulingConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("taskScheduler").hasBean("mcpEventTaskScheduler");
                    assertThat(context.getBean("taskScheduler")).isNotSameAs(context.getBean("mcpEventTaskScheduler"));
                    var scheduled = McpEventDispatcher.class.getMethod("dispatch")
                            .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
                    assertThat(scheduled.scheduler()).isEqualTo("mcpEventTaskScheduler");
                });
    }
    @Test
    void validatesSecretBoundsAndSignatureInputs() {
        for (String secret : List.of("", "whsec_!!!", "whsec_" + Base64.getEncoder().encodeToString(new byte[23]),
                "whsec_" + Base64.getEncoder().encodeToString(new byte[65])))
            assertThatThrownBy(() -> StandardWebhookSigner.key(secret)).isInstanceOf(McpEventException.class);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = StandardWebhookSigner.sign(SECRET, "evt_test", 123, body);
        assertThat(signature).isNotEqualTo(StandardWebhookSigner.sign(SECRET, "evt_other", 123, body));
        assertThat(signature).isNotEqualTo(StandardWebhookSigner.sign(SECRET, "evt_test", 124, body));
        assertThat(signature).isNotEqualTo(StandardWebhookSigner.sign(SECRET, "evt_test", 123, "{ }".getBytes(StandardCharsets.UTF_8)));
    }
}
