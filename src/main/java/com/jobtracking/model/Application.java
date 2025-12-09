package com.jobtracking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // Optional FK to Job - set when user applies from recommendations
    // NULL for manually added applications
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = true)
    @JsonIgnore
    private Job job;

    private String company;
    private String title;
    private String status; // DRAFT, APPLIED, INTERVIEW, OFFER, REJECTED
    
    @Column(name = "deadline_at")
    private LocalDate deadlineAt;
    
    @Column(name = "interview_at")
    private LocalDateTime interviewAt;
    
    private String location;
    
    @Column(name = "job_type")
    private String jobType; // Full-time, etc.
    
    private String salary;
    
    @Column(name = "job_link")
    private String jobLink;
    
    private String experience;
    
    @Column(name = "created_at")
    private LocalDate createdAt;
    
    @Column(name = "applied_at")
    private LocalDate appliedAt;
    
    @Column(columnDefinition = "TEXT")
    private String notes;

    // No-args constructor
    public Application() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDate deadlineAt) { this.deadlineAt = deadlineAt; }

    public LocalDateTime getInterviewAt() { return interviewAt; }
    public void setInterviewAt(LocalDateTime interviewAt) { this.interviewAt = interviewAt; }

    /**
     * Returns location - prefers Job FK's location if available
     */
    public String getLocation() { 
        if (job != null && job.getLocation() != null && !job.getLocation().isEmpty()) {
            return job.getLocation();
        }
        return location; 
    }
    public void setLocation(String location) { this.location = location; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    /**
     * Returns salary - prefers Job FK's salary if available
     */
    public String getSalary() { 
        if (job != null && job.getSalary() != null && !job.getSalary().isEmpty()) {
            return job.getSalary();
        }
        return salary; 
    }
    public void setSalary(String salary) { this.salary = salary; }

    /**
     * Returns the effective job link - prefers Job FK's externalUrl if available,
     * otherwise returns the manually entered jobLink
     */
    @JsonProperty("job_link")
    public String getJobLink() { 
        if (job != null && job.getExternalUrl() != null && !job.getExternalUrl().isEmpty()) {
            return job.getExternalUrl();
        }
        return jobLink; 
    }
    
    public void setJobLink(String jobLink) { this.jobLink = jobLink; }
    
    // Get raw manual job link (for internal use)
    @JsonIgnore
    public String getManualJobLink() { return jobLink; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }

    public LocalDate getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public UUID getUserId() { return user != null ? user.getId() : null; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }
    
    public UUID getJobId() { return job != null ? job.getId() : null; }
}

