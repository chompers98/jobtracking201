package com.jobtracking.controller;

import com.jobtracking.dto.JobDto;
import com.jobtracking.dto.JobRecommendationRequest;
import com.jobtracking.model.Job;
import com.jobtracking.service.JobRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    private final JobRecommendationService recommendationService;

    public JobController(JobRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    // Fetch all jobs (from DB)
    @GetMapping
    public List<Map<String, Object>> getJobs() {
        List<Job> jobs = recommendationService.getAllJobs();
        return jobs.stream().map(this::convertToMap).collect(Collectors.toList());
    }

    // Trigger Adzuna fetch
    // Example: POST /api/jobs/fetch?query=software&location=los+angeles
    @PostMapping("/fetch")
    public ResponseEntity<String> fetchJobs(
            @RequestParam(defaultValue = "software engineer") String query,
            @RequestParam(defaultValue = "United States") String location) {

        int count = recommendationService.refreshJobs(query, location);
        return ResponseEntity.ok("Fetched and saved " + count + " jobs");
    }

    // Get recommendations
    // Example: POST /api/jobs/recommendations?limit=20
    // Body: { "skills": ["python", "react", "backend"] }
    @PostMapping("/recommendations")
    public ResponseEntity<List<JobDto>> getRecommendations(
            @RequestBody JobRecommendationRequest request,
            @RequestParam(defaultValue = "20") int limit) {

        List<JobDto> results =
                recommendationService.recommendJobs(request.getSkills(), limit);
        return ResponseEntity.ok(results);
    }

    private Map<String, Object> convertToMap(Job job) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", job.getId());
        map.put("title", job.getTitle());
        map.put("company", job.getCompany());
        map.put("salary", job.getSalary());
        // job_type is not in Adzuna model currently, defaulting to empty or "Full-time" if you want
        map.put("job_type", "Full-time"); 
        map.put("location", job.getLocation());
        map.put("description", job.getDescription());
        map.put("job_link", job.getExternalUrl());
        return map;
    }
}
