package com.jobtracking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Junction table to track which jobs (from recommendations) each user has applied to.
 * This prevents showing the same job recommendation after a user clicks "Apply".
 */
@Entity
@Table(name = "user_applied_jobs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "job_id"})
})
public class UserAppliedJob {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    public UserAppliedJob() {}

    public UserAppliedJob(User user, Job job) {
        this.user = user;
        this.job = job;
        this.appliedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
