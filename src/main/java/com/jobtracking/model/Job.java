package com.jobtracking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column
    private String salary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String location;

    @Column(name = "external_url")
    private String externalUrl;

    public Job() {
    }

    public Job(String title,
               String company,
               String salary,
               String description,
               String location,
               String externalUrl) {
        this.title = title;
        this.company = company;
        this.salary = salary;
        this.description = description;
        this.location = location;
        this.externalUrl = externalUrl;
    }

    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }
}
