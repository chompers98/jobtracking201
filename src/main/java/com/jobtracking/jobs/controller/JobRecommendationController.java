package com.jobtracking.jobs.controller;

import com.jobtracking.jobs.dto.JobDto;
import com.jobtracking.jobs.dto.JobRecommendationRequest;
import com.jobtracking.jobs.service.JobRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class JobRecommendationController {

    private final JobRecommendationService recommendationService;

    public JobRecommendationController(JobRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    // Example: POST /api/jobs/fetch?query=software&location=los+angeles
    @PostMapping("/jobs/fetch")
    public ResponseEntity<String> fetchJobs(
            @RequestParam(defaultValue = "software engineer") String query,
            @RequestParam(defaultValue = "United States") String location) {

        int count = recommendationService.refreshJobs(query, location);
        return ResponseEntity.ok("Fetched and saved " + count + " jobs");
    }

    // Example: POST /api/recommendations?limit=20
    // Body: { "skills": ["python", "react", "backend"] }
    @PostMapping("/recommendations")
    public ResponseEntity<List<JobDto>> getRecommendations(
            @RequestBody JobRecommendationRequest request,
            @RequestParam(defaultValue = "20") int limit) {

        List<JobDto> results =
                recommendationService.recommendJobs(request.getSkills(), limit);
        return ResponseEntity.ok(results);
    }
}
