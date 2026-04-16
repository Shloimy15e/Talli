package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.PersonalAccessToken;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.PersonalAccessTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiTokenService {

    private final PersonalAccessTokenRepository tokenRepository;

    public ApiTokenService(PersonalAccessTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Generate a new personal access token. Returns the raw token string —
     * this is the only time the plaintext is available (we store only the hash).
     */
    @Transactional
    public String generate(User user, String name) {
        byte[] randomBytes = new byte[20];
        new SecureRandom().nextBytes(randomBytes);
        String rawToken = "talli_" + HexFormat.of().formatHex(randomBytes);

        PersonalAccessToken pat = new PersonalAccessToken();
        pat.setUser(user);
        pat.setName(name);
        pat.setTokenHash(sha256(rawToken));
        tokenRepository.save(pat);

        return rawToken;
    }

    /** Revoke (delete) a token, confirming it belongs to the given user. */
    @Transactional
    public void revoke(Long tokenId, User user) {
        tokenRepository.findById(tokenId)
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .ifPresent(tokenRepository::delete);
    }

    /** Look up a token by its raw (plaintext) value. Updates lastUsedAt on hit. */
    @Transactional
    public Optional<PersonalAccessToken> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("talli_")) {
            return Optional.empty();
        }
        Optional<PersonalAccessToken> found = tokenRepository.findByTokenHash(sha256(rawToken));
        found.ifPresent(t -> t.setLastUsedAt(LocalDateTime.now()));
        return found;
    }

    /** List all tokens for a user (for the profile page). */
    public List<PersonalAccessToken> tokensForUser(Long userId) {
        return tokenRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Deterministic SHA-256 hash — unlike BCrypt, same input always gives the same output. */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this cannot happen.
            throw new RuntimeException(e);
        }
    }
}
