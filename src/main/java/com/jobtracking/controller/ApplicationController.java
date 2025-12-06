package com.jobtracking.controller;

import com.jobtracking.dto.CalendarEventRequest;
import com.jobtracking.dto.CalendarEventResponse;
import com.jobtracking.model.Application;
import com.jobtracking.model.Reminder;
import com.jobtracking.model.User;
import com.jobtracking.repository.ApplicationRepository;
import com.jobtracking.repository.ReminderRepository;
import com.jobtracking.repository.UserRepository;
import com.jobtracking.service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend to connect
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final ReminderRepository reminderRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;

    public ApplicationController(ApplicationRepository applicationRepository,
                                 ReminderRepository reminderRepository,
                                 GoogleCalendarService googleCalendarService,
                                 UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.reminderRepository = reminderRepository;
        this.googleCalendarService = googleCalendarService;
        this.userRepository = userRepository;
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

    /**
     * Create a Google Calendar event for an application's reminder
     * POST /api/apps/{appId}/calendar
     * Body: { "reminderId": 1, "accessToken": "ya29.a0..." }
     */
    @PostMapping("/apps/{appId}/calendar")
    public ResponseEntity<CalendarEventResponse> createCalendarEvent(
            @PathVariable Long appId,
            @RequestBody CalendarEventRequest request) {

        try {
            // Verify application exists
            Application app = applicationRepository.findById(appId)
                    .orElseThrow(() -> new RuntimeException("Application not found"));

            // Find the reminder
            Reminder reminder = reminderRepository.findById(request.getReminderId())
                    .orElseThrow(() -> new RuntimeException("Reminder not found"));

            // Verify reminder belongs to this application
            if (!reminder.getApplicationId().equals(appId)) {
                return ResponseEntity.badRequest()
                        .body(new CalendarEventResponse(
                                false,
                                null,
                                "Reminder does not belong to this application"
                        ));
            }

            // Get current user's timezone (fallback to PST)
            String userTimezone = "America/Los_Angeles"; // Default
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    String username = auth.getName();
                    User user = userRepository.findByUsername(username).orElse(null);
                    if (user != null && user.getTimezone() != null) {
                        userTimezone = user.getTimezone();
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not get user timezone, using default: " + e.getMessage());
            }

            // Create calendar event
            String calendarLink = googleCalendarService.createCalendarEvent(
                    reminder,
                    request.getAccessToken(),
                    userTimezone
            );

            return ResponseEntity.ok(new CalendarEventResponse(
                    true,
                    calendarLink,
                    "Calendar event created successfully"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new CalendarEventResponse(
                            false,
                            null,
                            "Failed to create calendar event: " + e.getMessage()
                    ));
        }
    }
}