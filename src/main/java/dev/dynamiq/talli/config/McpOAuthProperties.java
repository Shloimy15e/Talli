package dev.dynamiq.talli.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
public final class McpOAuthProperties {

    private final String issuer;
    private final int dynamicClientLimit;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public McpOAuthProperties(
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.oauth.dynamic-client-limit:25}") int dynamicClientLimit,
            @Value("${app.oauth.access-token-ttl:PT1H}") Duration accessTokenTtl,
            @Value("${app.oauth.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.issuer = normalizeHttpsIssuer(baseUrl);
        if (dynamicClientLimit < 1) {
            throw new IllegalStateException("OAUTH_DYNAMIC_CLIENT_LIMIT must be positive");
        }
        if (accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalStateException("OAuth token lifetimes must be positive");
        }
        this.dynamicClientLimit = dynamicClientLimit;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String issuer() {
        return issuer;
    }

    public String resource() {
        return issuer + "/mcp";
    }

    public String protectedResourceMetadataUrl() {
        return issuer + "/.well-known/oauth-protected-resource";
    }

    public int dynamicClientLimit() {
        return dynamicClientLimit;
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    private static String normalizeHttpsIssuer(String value) {
        String normalized = value == null ? "" : value.strip().replaceAll("/+$", "");
        URI uri = URI.create(normalized);
        boolean localDevelopment = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        boolean originOnly = (uri.getPath() == null || uri.getPath().isEmpty())
                && uri.getQuery() == null
                && uri.getFragment() == null
                && uri.getUserInfo() == null;
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !localDevelopment)
                || uri.getHost() == null || normalized.isBlank() || !originOnly) {
            throw new IllegalStateException(
                    "APP_BASE_URL must be a public HTTPS origin (or localhost for development)");
        }
        return normalized;
    }
}
