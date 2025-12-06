package com.jobtracking.dto;

import java.util.List;

public class JobRecommendationRequest {

    // e.g. ["python", "react", "data"]
    private List<String> skills;

    public JobRecommendationRequest() {
    }

    public JobRecommendationRequest(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }
}
