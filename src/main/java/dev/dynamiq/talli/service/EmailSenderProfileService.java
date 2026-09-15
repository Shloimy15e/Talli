package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.EmailSenderProfile;
import dev.dynamiq.talli.repository.EmailSenderProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class EmailSenderProfileService {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_SIGNATURE_LENGTH = 100_000;

    private final EmailSenderProfileRepository repository;

    public EmailSenderProfileService(EmailSenderProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EmailSenderProfile> allProfiles() {
        return repository.findAllByOrderByDefaultSenderDescNameAsc();
    }

    /** Active sender choices for browser and agent composers. */
    @Transactional(readOnly = true)
    public List<EmailSenderProfile> options() {
        return repository.findAllByActiveTrueOrderByDefaultSenderDescNameAsc();
    }

    @Transactional(readOnly = true)
    public EmailSender defaultSender() {
        return repository.findFirstByDefaultSenderTrueAndActiveTrue()
                .map(EmailSenderProfile::toSender)
                .orElseThrow(() -> new IllegalStateException(
                        "No active default email sender is configured."));
    }

    @Transactional(readOnly = true)
    public EmailSender resolve(String requestedAddress) {
        if (requestedAddress == null || requestedAddress.isBlank()) {
            return defaultSender();
        }

        return repository.findByAddressIgnoreCaseAndActiveTrue(requestedAddress.trim())
                .map(EmailSenderProfile::toSender)
                .orElseThrow(() -> new IllegalArgumentException(
                        "senderEmail must be one of the active configured addresses: "
                                + String.join(", ", options().stream()
                                .map(EmailSenderProfile::getAddress)
                                .toList())));
    }

    @Transactional(readOnly = true)
    public EmailSenderProfile findRequired(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Email sender profile not found."));
    }

    @Transactional
    public EmailSenderProfile create(String address, String name, String signatureHtml,
                                     boolean defaultSender) {
        repository.lockAll();
        String normalizedAddress = normalizeAddress(address);
        String normalizedName = normalizeName(name);
        ensureUniqueAddress(normalizedAddress, null);

        boolean makeDefault = defaultSender
                || repository.findFirstByDefaultSenderTrueAndActiveTrue().isEmpty();
        if (makeDefault) repository.clearDefault();

        EmailSenderProfile profile = new EmailSenderProfile();
        apply(profile, normalizedAddress, normalizedName, signatureHtml, true, makeDefault);
        return save(profile);
    }

    @Transactional
    public EmailSenderProfile update(Long id, String address, String name, String signatureHtml,
                                     boolean defaultSender, boolean active) {
        repository.lockAll();
        EmailSenderProfile existing = findRequired(id);
        String normalizedAddress = normalizeAddress(address);
        String normalizedName = normalizeName(name);
        ensureUniqueAddress(normalizedAddress, id);

        if (existing.isDefaultSender() && !active) {
            throw new IllegalStateException(
                    "Choose another default sender before disabling this profile.");
        }
        if (defaultSender && !active) {
            throw new IllegalArgumentException("The default sender must be active.");
        }

        boolean makeDefault = active && (defaultSender || existing.isDefaultSender()
                || repository.findFirstByDefaultSenderTrueAndActiveTrue().isEmpty());
        if (makeDefault && !existing.isDefaultSender()) {
            repository.clearDefault();
            existing = findRequired(id);
        }

        apply(existing, normalizedAddress, normalizedName, signatureHtml, active, makeDefault);
        return save(existing);
    }

    @Transactional
    public EmailSenderProfile disable(Long id) {
        repository.lockAll();
        EmailSenderProfile profile = findRequired(id);
        if (profile.isDefaultSender()) {
            throw new IllegalStateException(
                    "Choose another default sender before disabling this profile.");
        }
        profile.setActive(false);
        return save(profile);
    }

    private static void apply(EmailSenderProfile profile, String address, String name,
                              String signatureHtml, boolean active, boolean defaultSender) {
        profile.setAddress(address);
        profile.setName(name);
        profile.setSignatureHtml(normalizeSignature(signatureHtml, address, name));
        profile.setActive(active);
        profile.setDefaultSender(defaultSender);
    }

    private void ensureUniqueAddress(String address, Long existingId) {
        boolean exists = existingId == null
                ? repository.existsByAddressIgnoreCase(address)
                : repository.existsByAddressIgnoreCaseAndIdNot(address, existingId);
        if (exists) {
            throw new IllegalArgumentException("A sender profile already uses this email address.");
        }
    }

    private EmailSenderProfile save(EmailSenderProfile profile) {
        try {
            return repository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException(
                    "The sender email or default selection conflicts with another profile.", exception);
        }
    }

    private static String normalizeAddress(String address) {
        String value = required(address, "Sender email").toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException("Enter one valid sender email address.");
        }
        return value;
    }

    private static String normalizeName(String name) {
        String value = required(name, "Sender name");
        if (value.length() > 200) {
            throw new IllegalArgumentException("Sender name must be 200 characters or fewer.");
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw new IllegalArgumentException("Sender name cannot contain angle brackets.");
        }
        return value;
    }

    private static String normalizeSignature(String signatureHtml, String address, String name) {
        if (signatureHtml == null || signatureHtml.isBlank()) {
            return EmailSender.basicSignatureHtml(address, name);
        }
        if (signatureHtml.length() > MAX_SIGNATURE_LENGTH) {
            throw new IllegalArgumentException("Signature must be 100,000 characters or fewer.");
        }
        return signatureHtml;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0
                || trimmed.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " cannot contain control characters.");
        }
        return trimmed;
    }
}
