package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.PersonalAccessToken;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.PersonalAccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiTokenServiceTest {

    private PersonalAccessTokenRepository tokenRepository;
    private ApiTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(PersonalAccessTokenRepository.class);
        when(tokenRepository.save(any(PersonalAccessToken.class))).thenAnswer(inv -> {
            PersonalAccessToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
        service = new ApiTokenService(tokenRepository);

        user = new User();
        user.setId(5L);
        user.setName("Test User");
    }

    @Test
    void generate_returnsRawTokenWithPrefix() {
        String rawToken = service.generate(user, "Chrome Extension");

        assertThat(rawToken).startsWith("talli_");
        assertThat(rawToken).hasSize(6 + 40); // "talli_" + 40 hex chars
    }

    @Test
    void generate_storesHashNotPlaintext() {
        String rawToken = service.generate(user, "My Token");

        verify(tokenRepository).save(argThat(pat -> {
            // Stored hash should NOT equal the raw token
            assertThat(pat.getTokenHash()).isNotEqualTo(rawToken);
            // Hash should be 64 chars (SHA-256 hex)
            assertThat(pat.getTokenHash()).hasSize(64);
            // Name and user should be set
            assertThat(pat.getName()).isEqualTo("My Token");
            assertThat(pat.getUser()).isSameAs(user);
            return true;
        }));
    }

    @Test
    void generate_producesUniqueTokensEachCall() {
        String token1 = service.generate(user, "Token 1");
        String token2 = service.generate(user, "Token 2");

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void authenticate_returnsTokenOnValidRawToken() {
        String rawToken = service.generate(user, "Test");
        String expectedHash = ApiTokenService.sha256(rawToken);

        PersonalAccessToken storedPat = new PersonalAccessToken();
        storedPat.setId(1L);
        storedPat.setUser(user);
        storedPat.setTokenHash(expectedHash);
        when(tokenRepository.findByTokenHash(expectedHash)).thenReturn(Optional.of(storedPat));

        Optional<PersonalAccessToken> result = service.authenticate(rawToken);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(storedPat);
        assertThat(storedPat.getLastUsedAt()).isNotNull();
    }

    @Test
    void authenticate_returnsEmptyOnBadToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        Optional<PersonalAccessToken> result = service.authenticate("talli_badbadbadbad");

        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_returnsEmptyOnNullToken() {
        assertThat(service.authenticate(null)).isEmpty();
    }

    @Test
    void authenticate_returnsEmptyOnWrongPrefix() {
        assertThat(service.authenticate("wrong_prefix_abc")).isEmpty();
        verify(tokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void revoke_deletesTokenBelongingToUser() {
        PersonalAccessToken pat = new PersonalAccessToken();
        pat.setId(10L);
        pat.setUser(user);
        when(tokenRepository.findById(10L)).thenReturn(Optional.of(pat));

        service.revoke(10L, user);

        verify(tokenRepository).delete(pat);
    }

    @Test
    void revoke_doesNothingWhenTokenBelongsToDifferentUser() {
        User otherUser = new User();
        otherUser.setId(99L);

        PersonalAccessToken pat = new PersonalAccessToken();
        pat.setId(10L);
        pat.setUser(otherUser);
        when(tokenRepository.findById(10L)).thenReturn(Optional.of(pat));

        service.revoke(10L, user);

        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void tokensForUser_delegatesToRepository() {
        when(tokenRepository.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        List<PersonalAccessToken> result = service.tokensForUser(5L);

        assertThat(result).isEmpty();
        verify(tokenRepository).findByUserIdOrderByCreatedAtDesc(5L);
    }

    @Test
    void sha256_isDeterministic() {
        String hash1 = ApiTokenService.sha256("talli_abc123");
        String hash2 = ApiTokenService.sha256("talli_abc123");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void sha256_differentInputsProduceDifferentHashes() {
        String hash1 = ApiTokenService.sha256("talli_abc123");
        String hash2 = ApiTokenService.sha256("talli_xyz789");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
