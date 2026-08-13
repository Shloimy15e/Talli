package dev.dynamiq.talli.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "tax_id")
    private String taxId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Net-X payment terms in days. Used to compute invoice due date at generation time. */
    @Column(name = "payment_terms_days", nullable = false)
    private Integer paymentTermsDays = 30;

    @Column(name = "reminders_enabled", nullable = false)
    private Boolean remindersEnabled = true;

    /** Override global reminder interval. Null = use global default. */
    @Column(name = "reminder_interval_days")
    private Integer reminderIntervalDays;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Auto-set timestamps on insert/update
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public Boolean getRemindersEnabled() { return remindersEnabled; }
    public void setRemindersEnabled(Boolean remindersEnabled) { this.remindersEnabled = remindersEnabled; }

    public Integer getReminderIntervalDays() { return reminderIntervalDays; }
    public void setReminderIntervalDays(Integer reminderIntervalDays) { this.reminderIntervalDays = reminderIntervalDays; }

    public LocalDateTime getLastReminderAt() { return lastReminderAt; }
    public void setLastReminderAt(LocalDateTime lastReminderAt) { this.lastReminderAt = lastReminderAt; }

    public void setPaymentTermsDays(Integer paymentTermsDays) {
        this.paymentTermsDays = paymentTermsDays;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
