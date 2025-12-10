package com.jobtracking.controller;

import com.jobtracking.dto.CalendarEventRequest;
import com.jobtracking.dto.CalendarEventResponse;
import com.jobtracking.model.Application;
import com.jobtracking.model.Reminder;
import com.jobtracking.model.User;
import com.jobtracking.repository.ApplicationRepository;
import com.jobtracking.repository.ReminderRepository;
import com.jobtracking.repository.UserRepository;
import com.jobtracking.service.ApplicationAutoReminderService;
import com.jobtracking.service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend to connect
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final ReminderRepository reminderRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;
    private final ApplicationAutoReminderService autoReminderService;

    public ApplicationController(ApplicationRepository applicationRepository,
                                 ReminderRepository reminderRepository,
                                 GoogleCalendarService googleCalendarService,
                                 UserRepository userRepository,
                                 ApplicationAutoReminderService autoReminderService) {
        this.applicationRepository = applicationRepository;
        this.reminderRepository = reminderRepository;
        this.googleCalendarService = googleCalendarService;
        this.userRepository = userRepository;
        this.autoReminderService = autoReminderService;
    }

    /**
     * Helper method to get the current authenticated user
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        String principal = auth.getName();
        // Try email first (JWT stores email), then fall back to username
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElseThrow(() -> new RuntimeException("User not found: " + principal));
    }

    @GetMapping("/apps")
    public List<Application> getAllApplications() {
        User currentUser = getCurrentUser();
        return applicationRepository.findByUser_Id(currentUser.getId());
    }

    /**
     * Get calendar events (reminders) for the current user
     * This must be defined BEFORE /apps/{id} to avoid UUID parsing issues
     */
    @GetMapping("/apps/calendar")
    public List<java.util.Map<String, Object>> getCalendarEvents() {
        User currentUser = getCurrentUser();

        // Fetch reminders directly by user_id
        List<Reminder> reminders = reminderRepository.findByUser_Id(currentUser.getId());

        // Transform reminders to calendar event format expected by frontend
        return reminders.stream().map(r -> {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("id", r.getId());
            event.put("title", r.getTitle());
            event.put("date", r.getTriggerAt() != null ? r.getTriggerAt().toString() : null);
            event.put("color", r.getColor());
            event.put("kind", r.getKind());
            event.put("application_id", r.getApplicationId());
            event.put("notes", r.getNotes());

            // Add subtitle for interviews with time
            if ("INTERVIEW".equals(r.getKind()) && r.getStartTime() != null) {
                event.put("subtitle", r.getStartTime());
            }

            // Interview-specific fields
            event.put("end_date", r.getEndDate() != null ? r.getEndDate().toString() : null);
            event.put("start_time", r.getStartTime());
            event.put("end_time", r.getEndTime());
            event.put("location", r.getLocation());
            event.put("meeting_link", r.getMeetingLink());
            event.put("trigger_at", r.getTriggerAt() != null ? r.getTriggerAt().toString() : null);

            return event;
        }).toList();
    }

    /**
     * Get dashboard summary metrics
     */
    @GetMapping("/dashboard-summary")
    public java.util.Map<String, Object> getDashboardSummary() {
        User currentUser = getCurrentUser();
        List<Application> apps = applicationRepository.findByUser_Id(currentUser.getId());

        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalApplications", apps.size());

        // Count by status
        java.util.Map<String, Long> byStatus = apps.stream()
                .filter(a -> a.getStatus() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Application::getStatus,
                        java.util.stream.Collectors.counting()
                ));
        summary.put("byStatus", byStatus);

        return summary;
    }

    @PostMapping("/apps")
    public Application createApplication(@RequestBody Application application) {
        User currentUser = getCurrentUser();
        application.setUser(currentUser);

        if (application.getCreatedAt() == null) {
            application.setCreatedAt(LocalDate.now());
        }
        if (application.getStatus() == null) {
            application.setStatus("DRAFT");
        }

        // Save application first
        Application savedApplication = applicationRepository.save(application);

        // Auto-generate reminders from application data
        try {
            autoReminderService.generateRemindersForApplication(savedApplication, currentUser);
        } catch (Exception e) {
            System.err.println("Failed to auto-generate reminders: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the request - application is still created
        }

        return savedApplication;
    }

    @GetMapping("/apps/{id}")
    public ResponseEntity<Application> getApplication(@PathVariable UUID id) {
        User currentUser = getCurrentUser();
        return applicationRepository.findById(id)
                .filter(app -> app.getUser().getId().equals(currentUser.getId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get reminders for a specific application
     */
    @GetMapping("/apps/{id}/reminders")
    public ResponseEntity<List<Reminder>> getApplicationReminders(@PathVariable UUID id) {
        User currentUser = getCurrentUser();
        // Verify the application belongs to the current user
        return applicationRepository.findById(id)
                .filter(app -> app.getUser().getId().equals(currentUser.getId()))
                .map(app -> ResponseEntity.ok(reminderRepository.findByApplicationId(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/apps/{id}")
    public ResponseEntity<Application> updateApplication(@PathVariable UUID id, @RequestBody Application appDetails) {
        User currentUser = getCurrentUser();
        return applicationRepository.findById(id)
                .filter(app -> app.getUser().getId().equals(currentUser.getId()))
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

                    // Save updated application
                    Application updatedApp = applicationRepository.save(app);

                    // Regenerate reminders if deadline or interview changed
                    try {
                        autoReminderService.generateRemindersForApplication(updatedApp, currentUser);
                    } catch (Exception e) {
                        System.err.println("Failed to auto-generate reminders on update: " + e.getMessage());
                        e.printStackTrace();
                        // Don't fail the request - application is still updated
                    }

                    return ResponseEntity.ok(updatedApp);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/apps/{id}/status")
    public ResponseEntity<Application> updateStatus(@PathVariable UUID id, @RequestBody Application statusUpdate) {
        User currentUser = getCurrentUser();
        return applicationRepository.findById(id)
                .filter(app -> app.getUser().getId().equals(currentUser.getId()))
                .map(app -> {
                    app.setStatus(statusUpdate.getStatus());
                    return ResponseEntity.ok(applicationRepository.save(app));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/apps/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable UUID id) {
        User currentUser = getCurrentUser();
        return applicationRepository.findById(id)
                .filter(app -> app.getUser().getId().equals(currentUser.getId()))
                .map(app -> {
                    try {
                        // Remove associated reminders
                        autoReminderService.removeRemindersForApplication(id);
                    } catch (Exception e) {
                        System.err.println("Failed to remove reminders: " + e.getMessage());
                        e.printStackTrace();
                        // Don't fail the request - application is still deleted
                    }

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
            @PathVariable UUID appId,
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