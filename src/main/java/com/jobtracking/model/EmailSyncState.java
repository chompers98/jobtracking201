package com.jobtracking.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_sync_state")
public class EmailSyncState {

    @Id
    private Long id = 1L;  // always a single row

    // we'll store the last Gmail internalDate we processed
    private Long lastProcessedInternalDate;

    public EmailSyncState() {
    }

    public Long getId() {
        return id;
    }

    public Long getLastProcessedInternalDate() {
        return lastProcessedInternalDate;
    }

    public void setLastProcessedInternalDate(Long lastProcessedInternalDate) {
        this.lastProcessedInternalDate = lastProcessedInternalDate;
    }
}