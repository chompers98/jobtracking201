package com.jobtracking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.util.UUID;

/**
 * Tracks email sync state per user.
 * Each user has their own lastProcessedInternalDate for their Gmail account.
 */
@Entity
@Table(name = "email_sync_state")
public class EmailSyncState {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // we'll store the last Gmail internalDate we processed for this user
    private Long lastProcessedInternalDate;

    public EmailSyncState() {
    }

    public EmailSyncState(User user) {
        this.user = user;
        this.lastProcessedInternalDate = 0L;
    }

    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getLastProcessedInternalDate() {
        return lastProcessedInternalDate;
    }

    public void setLastProcessedInternalDate(Long lastProcessedInternalDate) {
        this.lastProcessedInternalDate = lastProcessedInternalDate;
    }
}