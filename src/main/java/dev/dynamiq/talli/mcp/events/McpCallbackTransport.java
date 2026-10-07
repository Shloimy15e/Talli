package dev.dynamiq.talli.mcp.events;

import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** HTTPS transport with DNS validation and address pinning; never follows redirects. */
@Component
public class McpCallbackTransport {
    static final int MAX_BYTES = 262_144;
    private static final ExecutorService DNS = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "mcp-callback-dns"); t.setDaemon(true); return t; });
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mcp-callback-deadline"); t.setDaemon(true); return t;
    });
    @FunctionalInterface interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }
    private final Resolver resolver;
    private final SSLSocketFactory sslFactory;
    private final Supplier<Socket> sockets;
    public McpCallbackTransport() {
        this(InetAddress::getAllByName, (SSLSocketFactory) SSLSocketFactory.getDefault(), Socket::new);
    }
    McpCallbackTransport(Resolver resolver, SSLSocketFactory sslFactory, Supplier<Socket> sockets) {
        this.resolver = resolver;
        this.sslFactory = sslFactory;
        this.sockets = sockets;
    }
    public record Response(int status, byte[] body) { }

    static URI validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535)
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new McpEventException(-32602, "invalid_callback", "Callback must be an absolute HTTPS URL without credentials or fragment");
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        int a = b[0] & 255, c = b[1] & 255;
        if (b.length == 4) {
            int d = b[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && c >= 64 && c <= 127)
                    || (a == 169 && c == 254) || (a == 172 && c >= 16 && c <= 31)
                    || (a == 192 && (c == 168 || (c == 88 && d == 99) || (c == 0 && (d == 0 || d == 2))))
                    || (a == 198 && (c == 18 || c == 19 || (c == 51 && d == 100)))
                    || (a == 203 && c == 0 && d == 113));
        }
        // Only global unicast; exclude special-purpose, documentation and transition ranges.
        return (a & 0xe0) == 0x20
                && !(a == 0x20 && c == 1 && ((b[2] & 255) < 2 || ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)))
                && !(a == 0x20 && c == 2) && !(a == 0x3f && c == 0xff);
    }

    public Response post(String url, String subscriptionId, String eventId, byte[] body,
                         String secret, String previousSecret) throws IOException {
        if (body.length > MAX_BYTES) throw new IOException("Payload exceeds limit");
        URI uri = validateUrl(url);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String host = uri.getHost().replace("[", "").replace("]", "");
        InetAddress[] addresses;
        Future<InetAddress[]> lookup;
        try { lookup = DNS.submit(() -> resolver.resolve(host)); }
        catch (RejectedExecutionException e) { throw new IOException("DNS capacity exceeded", e); }
        try { addresses = lookup.get(remaining(deadline), TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); lookup.cancel(true); throw new IOException("Interrupted", e); }
        catch (TimeoutException e) { lookup.cancel(true); throw new SocketTimeoutException("Callback DNS timed out"); }
        catch (ExecutionException e) { lookup.cancel(true); throw new IOException("DNS resolution failed", e); }
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(a -> !isPublic(a)))
            throw new McpEventException(-32015, "unsafe_address", "Callback resolves to a non-public address");
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        try (Socket socket = connect(addresses, port, deadline)) {
            ScheduledFuture<?> timeout = DEADLINES.schedule(() -> {
                try { socket.close(); } catch (IOException ignored) { }
            }, remaining(deadline), TimeUnit.MILLISECONDS);
            try {
                socket.setSoTimeout(remaining(deadline));
                try (SSLSocket tls = (SSLSocket) sslFactory.createSocket(socket, host, port, true)) {
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    if (!host.contains(":")) parameters.setServerNames(List.of(new SNIHostName(host)));
                    tls.setSSLParameters(parameters);
                    tls.startHandshake();
                    long timestamp = Instant.now().getEpochSecond();
                    String signature = StandardWebhookSigner.sign(secret, eventId, timestamp, body);
                    if (previousSecret != null) signature += " " + StandardWebhookSigner.sign(previousSecret, eventId, timestamp, body);
                    String path = uri.getRawPath();
                    if (path == null || path.isEmpty()) path = "/";
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                    String headers = "POST " + path + " HTTP/1.1\r\nHost: " + uri.getRawAuthority()
                            + "\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: " + body.length
                            + "\r\nwebhook-id: " + eventId + "\r\nwebhook-timestamp: " + timestamp
                            + "\r\nwebhook-signature: " + signature + "\r\nX-MCP-Subscription-Id: " + subscriptionId + "\r\n\r\n";
                    OutputStream output = tls.getOutputStream();
                    output.write(headers.getBytes(StandardCharsets.US_ASCII));
                    output.write(body);
                    output.flush();
                    return readResponse(tls.getInputStream());
                }
            } finally { timeout.cancel(false); }
        }
    }
    private Socket connect(InetAddress[] addresses, int port, long deadline) throws IOException {
        IOException last = null;
        for (InetAddress address : addresses) {
            Socket socket = sockets.get();
            try {
                socket.connect(new InetSocketAddress(address, port), Math.min(2000, remaining(deadline)));
                return socket;
            } catch (IOException e) {
                last = e;
                try { socket.close(); } catch (IOException ignored) { }
            }
        }
        throw last == null ? new IOException("No callback addresses") : last;
    }
    private static int remaining(long deadline) throws SocketTimeoutException {
        long millis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (millis <= 0) throw new SocketTimeoutException("Callback timed out");
        return (int) Math.min(millis, 10_000);
    }
    static Response readResponse(InputStream input) throws IOException {
        String statusLine = line(input);
        String[] statusParts = statusLine.split(" ", 3);
        if (statusParts.length < 2 || !statusParts[0].matches("HTTP/1\\.[01]")) throw new IOException("Invalid callback response");
        int status;
        try { status = Integer.parseInt(statusParts[1]); } catch (NumberFormatException e) { throw new IOException("Invalid status", e); }
        Map<String, String> headers = new HashMap<>();
        int headerBytes = statusLine.length();
        for (String line; !(line = line(input)).isEmpty();) {
            headerBytes += line.length();
            if (headerBytes > 16_384) throw new IOException("Response headers exceed limit");
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IOException("Invalid response header");
            headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        // Delivery status is sufficient for errors and bodyless acknowledgments.
        if (status < 200 || status >= 300 || status == 204) return new Response(status, new byte[0]);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (headers.containsKey("transfer-encoding")) {
            if (!"chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) throw new IOException("Unsupported transfer encoding");
            while (true) {
                String sizeLine = line(input).split(";", 2)[0];
                int size;
                try { size = Integer.parseInt(sizeLine, 16); } catch (NumberFormatException e) { throw new IOException("Invalid chunk", e); }
                if (size < 0 || size > MAX_BYTES - body.size()) throw new IOException("Response exceeds limit");
                if (size == 0) break;
                copy(input, body, size);
                if (!line(input).isEmpty()) throw new IOException("Invalid chunk ending");
            }
        } else if (headers.containsKey("content-length")) {
            int length;
            try { length = Integer.parseInt(headers.get("content-length")); } catch (NumberFormatException e) { throw new IOException("Invalid content length", e); }
            if (length < 0 || length > MAX_BYTES) throw new IOException("Response exceeds limit");
            copy(input, body, length);
        } else {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Response exceeds limit");
            body.write(bytes);
        }
        return new Response(status, body.toByteArray());
    }
    private static void copy(InputStream input, OutputStream output, int count) throws IOException {
        byte[] bytes = input.readNBytes(count);
        if (bytes.length != count) throw new EOFException("Incomplete callback response");
        output.write(bytes);
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int next; (next = input.read()) != -1;) {
            if (next == '\n') {
                byte[] line = bytes.toByteArray();
                if (line.length == 0 || line[line.length - 1] != '\r') throw new IOException("Invalid HTTP line");
                return new String(line, 0, line.length - 1, StandardCharsets.US_ASCII);
            }
            if (bytes.size() >= 8192) throw new IOException("HTTP line exceeds limit");
            bytes.write(next);
        }
        throw new EOFException("Incomplete callback response");
    }
}
