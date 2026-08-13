package dev.dynamiq.talli.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OAuthAuthorizationServerConfig {

    @Bean
    @Order(1)
    SecurityFilterChain oauthAuthorizationServerFilterChain(
            HttpSecurity http,
            RegisteredClientRepository registeredClients,
            OAuth2AuthorizationService authorizations,
            OAuth2AuthorizationConsentService consents,
            AuthorizationServerSettings settings,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            OAuthResourceParameterFilter resourceParameterFilter,
            McpOAuthProperties properties) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
            .securityMatcher(new OrRequestMatcher(
                    authorizationServer.getEndpointsMatcher(),
                    new AntPathRequestMatcher("/oauth/register"),
                    new AntPathRequestMatcher("/.well-known/oauth-protected-resource"),
                    new AntPathRequestMatcher("/.well-known/oauth-protected-resource/**")))
            .with(authorizationServer, server -> server
                    .registeredClientRepository(registeredClients)
                    .authorizationService(authorizations)
                    .authorizationConsentService(consents)
                    .authorizationServerSettings(settings)
                    .tokenGenerator(tokenGenerator)
                    .authorizationServerMetadataEndpoint(metadata -> metadata
                            .authorizationServerMetadataCustomizer(builder -> builder
                                    .clientRegistrationEndpoint(properties.issuer() + "/oauth/register")
                                    .scope("mcp")
                                    .scope("offline_access")
                                    .claim("resource_parameter_supported", true))))
            .addFilterBefore(resourceParameterFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/oauth/register", "/.well-known/oauth-protected-resource",
                            "/.well-known/oauth-protected-resource/**").permitAll()
                    .anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    new AntPathRequestMatcher("/oauth/register"),
                    new AntPathRequestMatcher("/.well-known/oauth-protected-resource"),
                    new AntPathRequestMatcher("/.well-known/oauth-protected-resource/**")))
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    OAuth2AuthorizationService oauth2AuthorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClients) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClients);
    }

    @Bean
    OAuth2AuthorizationConsentService oauth2AuthorizationConsentService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClients);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(McpOAuthProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.issuer())
                .authorizationEndpoint("/oauth/authorize")
                .tokenEndpoint("/oauth/token")
                .tokenRevocationEndpoint("/oauth/revoke")
                .tokenIntrospectionEndpoint("/oauth/introspect")
                .build();
    }

    @Bean
    OAuth2TokenCustomizer<OAuth2TokenClaimsContext> oauthAccessTokenCustomizer(
            McpOAuthProperties properties) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            context.getClaims()
                    .audience(new ArrayList<>(List.of(properties.resource())))
                    .claim("resource", properties.resource());
        };
    }

    @Bean
    OAuth2TokenGenerator<? extends OAuth2Token> oauth2TokenGenerator(
            OAuth2TokenCustomizer<OAuth2TokenClaimsContext> accessTokenCustomizer) {
        OAuth2AccessTokenGenerator accessTokens = new OAuth2AccessTokenGenerator();
        accessTokens.setAccessTokenCustomizer(accessTokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(accessTokens, new OAuth2RefreshTokenGenerator());
    }
}
