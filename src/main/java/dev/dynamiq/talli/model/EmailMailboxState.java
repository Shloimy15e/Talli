package dev.dynamiq.talli.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_mailbox_states", uniqueConstraints =
        @UniqueConstraint(name = "uq_email_mailbox_states_user_thread", columnNames = {"user_id", "thread_root_id"}))
public class EmailMailboxState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "thread_root_id", nullable = false)
    private Long threadRootId;

    @Column(name = "last_read_email_id")
    private Long lastReadEmailId;

    @Column(name = "archived_through_email_id")
    private Long archivedThroughEmailId;

    @Column(nullable = false)
    private boolean starred;

    @Column(name = "marked_unread", nullable = false)
    private boolean markedUnread;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getThreadRootId() { return threadRootId; }
    public void setThreadRootId(Long threadRootId) { this.threadRootId = threadRootId; }

    public Long getLastReadEmailId() { return lastReadEmailId; }
    public void setLastReadEmailId(Long lastReadEmailId) { this.lastReadEmailId = lastReadEmailId; }

    public Long getArchivedThroughEmailId() { return archivedThroughEmailId; }
    public void setArchivedThroughEmailId(Long archivedThroughEmailId) { this.archivedThroughEmailId = archivedThroughEmailId; }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }

    public boolean isMarkedUnread() { return markedUnread; }
    public void setMarkedUnread(boolean markedUnread) { this.markedUnread = markedUnread; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
