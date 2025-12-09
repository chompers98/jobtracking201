package com.jobtracking.controller;

import com.jobtracking.dto.JobDto;
import com.jobtracking.dto.JobRecommendationRequest;
import com.jobtracking.model.Application;
import com.jobtracking.model.Job;
import com.jobtracking.model.User;
import com.jobtracking.model.UserAppliedJob;
import com.jobtracking.repository.ApplicationRepository;
import com.jobtracking.repository.JobRepository;
import com.jobtracking.repository.UserAppliedJobRepository;
import com.jobtracking.repository.UserRepository;
import com.jobtracking.service.JobRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    private final JobRecommendationService recommendationService;
    private final UserRepository userRepository;
    private final UserAppliedJobRepository userAppliedJobRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;

    public JobController(JobRecommendationService recommendationService,
                        UserRepository userRepository,
                        UserAppliedJobRepository userAppliedJobRepository,
                        JobRepository jobRepository,
                        ApplicationRepository applicationRepository) {
        this.recommendationService = recommendationService;
        this.userRepository = userRepository;
        this.userAppliedJobRepository = userAppliedJobRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * Helper method to get the current authenticated user
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    // Fetch all jobs (from DB), excluding jobs the user has already applied to
    @GetMapping
    public List<Map<String, Object>> getJobs() {
        User currentUser = getCurrentUser();
        List<Job> jobs = recommendationService.getAllJobs();
        
        // Get job IDs that the user has already applied to
        Set<UUID> appliedJobIds = new HashSet<>(
            userAppliedJobRepository.findJobIdsByUserId(currentUser.getId())
        );
        
        // Filter out jobs that the user has already applied to
        return jobs.stream()
                .filter(job -> !appliedJobIds.contains(job.getId()))
                .map(this::convertToMap)
                .collect(Collectors.toList());
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

    // Get recommendations, excluding jobs the user has already applied to
    // Example: POST /api/jobs/recommendations?limit=20
    // Body: { "skills": ["python", "react", "backend"] }
    @PostMapping("/recommendations")
    public ResponseEntity<List<JobDto>> getRecommendations(
            @RequestBody JobRecommendationRequest request,
            @RequestParam(defaultValue = "20") int limit) {

        User currentUser = getCurrentUser();
        
        // Get job IDs that the user has already applied to
        Set<UUID> appliedJobIds = new HashSet<>(
            userAppliedJobRepository.findJobIdsByUserId(currentUser.getId())
        );

        List<JobDto> results = recommendationService.recommendJobs(request.getSkills(), limit);
        
        // Filter out jobs that the user has already applied to
        List<JobDto> filteredResults = results.stream()
                .filter(job -> !appliedJobIds.contains(job.getId()))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(filteredResults);
    }

    /**
     * Apply to a job - moves the job from recommendations to applications for the current user
     * POST /api/jobs/{jobId}/apply
     */
    @PostMapping("/{jobId}/apply")
    public ResponseEntity<Map<String, Object>> applyToJob(@PathVariable UUID jobId) {
        User currentUser = getCurrentUser();
        
        // Check if job exists
        Job job = jobRepository.findById(jobId)
                .orElse(null);
        
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user has already applied to this job
        if (userAppliedJobRepository.existsByUser_IdAndJob_Id(currentUser.getId(), jobId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "You have already applied to this job");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Create an Application linked to the Job (FK relationship)
        Application application = new Application();
        application.setUser(currentUser);
        application.setJob(job); // Link to Job via FK - gives access to all job info
        
        // Copy basic info for display purposes (in case job is deleted later)
        application.setCompany(job.getCompany());
        application.setTitle(job.getTitle());
        
        // Don't copy location/salary/link - access through job FK instead
        application.setNotes("[Applied from job recommendation]\n" + 
                           (job.getDescription() != null ? job.getDescription() : ""));
        application.setStatus("APPLIED");
        application.setCreatedAt(LocalDate.now());
        application.setAppliedAt(LocalDate.now());
        
        applicationRepository.save(application);
        
        // Track that this user has applied to this job
        UserAppliedJob userAppliedJob = new UserAppliedJob(currentUser, job);
        userAppliedJobRepository.save(userAppliedJob);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Successfully applied to job");
        response.put("applicationId", application.getId());
        
        return ResponseEntity.ok(response);
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
