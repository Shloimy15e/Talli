package dev.dynamiq.talli.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emails")
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;  // optional

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;  // optional — set when this email was sent for a specific invoice

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    /** 'out' for emails we sent, 'in' for emails received via the inbound webhook. */
    @Column(nullable = false)
    private String direction = "out";

    /** For inbound emails, the sender's address. Null for outbound (we're the sender). */
    @Column(name = "from_address")
    private String fromAddress;

    /** When an inbound email was received (set from the webhook event). */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** Comma-separated BCC addresses. */
    @Column(columnDefinition = "TEXT")
    private String bcc;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Rendered HTML body (with signature baked in). Null if email was sent as plain text only. */
    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resend_id")
    private String resendId;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "complained_at")
    private LocalDateTime complainedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "bounce_reason", columnDefinition = "TEXT")
    private String bounceReason;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getBcc() {
        return bcc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getResendId() {
        return resendId;
    }

    public void setResendId(String resendId) {
        this.resendId = resendId;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getBouncedAt() {
        return bouncedAt;
    }

    public void setBouncedAt(LocalDateTime bouncedAt) {
        this.bouncedAt = bouncedAt;
    }

    public LocalDateTime getComplainedAt() {
        return complainedAt;
    }

    public void setComplainedAt(LocalDateTime complainedAt) {
        this.complainedAt = complainedAt;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }

    public String getBounceReason() {
        return bounceReason;
    }

    public void setBounceReason(String bounceReason) {
        this.bounceReason = bounceReason;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
