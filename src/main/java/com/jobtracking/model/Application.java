package com.jobtracking.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }

    public String getJobLink() { return jobLink; }
    public void setJobLink(String jobLink) { this.jobLink = jobLink; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }

    public LocalDate getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

