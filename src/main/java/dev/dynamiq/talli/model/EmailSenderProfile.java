package dev.dynamiq.talli.model;

import dev.dynamiq.talli.service.EmailSender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_sender_profiles")
public class EmailSenderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String address;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "signature_html", nullable = false, columnDefinition = "TEXT")
    private String signatureHtml;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSender;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public EmailSender toSender() {
        return new EmailSender(address, name, signatureHtml);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSignatureHtml() { return signatureHtml; }
    public void setSignatureHtml(String signatureHtml) { this.signatureHtml = signatureHtml; }
    public boolean isDefaultSender() { return defaultSender; }
    public void setDefaultSender(boolean defaultSender) { this.defaultSender = defaultSender; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
