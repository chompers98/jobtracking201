package com.jobtracking.controller;

import com.jobtracking.model.Application;
import com.jobtracking.repository.ApplicationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend to connect
public class ApplicationController {

    private final ApplicationRepository applicationRepository;

    public ApplicationController(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/apps")
    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    @PostMapping("/apps")
    public Application createApplication(@RequestBody Application application) {
        if (application.getCreatedAt() == null) {
            application.setCreatedAt(LocalDate.now());
        }
        if (application.getStatus() == null) {
            application.setStatus("DRAFT");
        }
        return applicationRepository.save(application);
    }

    @GetMapping("/apps/{id}")
    public ResponseEntity<Application> getApplication(@PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/apps/{id}")
    public ResponseEntity<Application> updateApplication(@PathVariable Long id, @RequestBody Application appDetails) {
        return applicationRepository.findById(id)
                .map(app -> {
                    app.setCompany(appDetails.getCompany());
                    app.setTitle(appDetails.getTitle());
                    app.setStatus(appDetails.getStatus());
                    app.setDeadlineAt(appDetails.getDeadlineAt());
                    app.setInterviewAt(appDetails.getInterviewAt());
                    app.setLocation(appDetails.getLocation());
                    app.setJobType(appDetails.getJobType());
                    app.setSalary(appDetails.getSalary());
                    app.setJobLink(appDetails.getJobLink());
                    app.setExperience(appDetails.getExperience());
                    app.setNotes(appDetails.getNotes());
                    // Update other fields as needed
                    return ResponseEntity.ok(applicationRepository.save(app));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/apps/{id}/status")
    public ResponseEntity<Application> updateStatus(@PathVariable Long id, @RequestBody Application statusUpdate) {
         return applicationRepository.findById(id)
                .map(app -> {
                    app.setStatus(statusUpdate.getStatus());
                    return ResponseEntity.ok(applicationRepository.save(app));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/apps/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(app -> {
                    applicationRepository.delete(app);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

