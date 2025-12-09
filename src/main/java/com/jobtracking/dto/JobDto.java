package com.jobtracking.dto;

import java.util.UUID;

public class JobDto {

    private UUID id;
    private String title;
    private String company;
    private String salary;
    private String description;
    private String location;
    private String externalUrl;
    private int score; // how well it matches user skills

    public JobDto() {
    }

    public JobDto(UUID id,
                  String title,
                  String company,
                  String salary,
                  String description,
                  String location,
                  String externalUrl,
                  int score) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.salary = salary;
        this.description = description;
        this.location = location;
        this.externalUrl = externalUrl;
        this.score = score;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getSalary() {
        return salary;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
