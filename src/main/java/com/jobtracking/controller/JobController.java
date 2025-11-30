package com.jobtracking.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    // Hardcoded mock data to match original app.py
    @GetMapping
    public List<Map<String, Object>> getJobs() {
        List<Map<String, Object>> jobs = new ArrayList<>();
        
        jobs.add(createJob(1, "Frontend Developer", "Tech Corp", "$100k - $140k", "Full-time", "San Francisco, CA"));
        jobs.add(createJob(2, "Backend Engineer", "Data Systems", "$120k - $160k", "Full-time", "Remote"));
        jobs.add(createJob(3, "Product Designer", "Creative Solutions", "$90k - $130k", "Contract", "New York, NY"));
        jobs.add(createJob(4, "DevOps Engineer", "Cloud Infra", "$130k - $170k", "Full-time", "Austin, TX"));
        
        return jobs;
    }
    
    private Map<String, Object> createJob(int id, String title, String company, String salary, String type, String location) {
        Map<String, Object> job = new HashMap<>();
        job.put("id", id);
        job.put("title", title);
        job.put("company", company);
        job.put("salary", salary);
        job.put("job_type", type);
        job.put("location", location);
        job.put("description", "Description for " + title);
        job.put("job_link", "https://example.com/jobs/" + id);
        return job;
    }
}

