package com.jobtracking.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kind; // DEADLINE, INTERVIEW, FOLLOWUP
    private String title;
    
    @Column(name = "application_id")
    private Long applicationId;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    private String color;
    
    @Column(name = "created_at")
    private LocalDate createdAt;
    
    @Column(name = "trigger_at")
    private LocalDate triggerAt;
    
    @Column(name = "sent_at")
    private LocalDate sentAt;
    
    // Interview specific
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(name = "start_time")
    private String startTime; // Storing as string to match python "14:00"
    
    @Column(name = "end_time")
    private String endTime;
    
    private String location;
    
    @Column(name = "meeting_link")
    private String meetingLink;

    public Reminder() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
    
    public LocalDate getTriggerAt() { return triggerAt; }
    public void setTriggerAt(LocalDate triggerAt) { this.triggerAt = triggerAt; }
    
    public LocalDate getSentAt() { return sentAt; }
    public void setSentAt(LocalDate sentAt) { this.sentAt = sentAt; }
    
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }
}

