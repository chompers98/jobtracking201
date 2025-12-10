package com.jobtracking.controller;

import com.jobtracking.dto.CalendarEventRequest;
import com.jobtracking.dto.CalendarEventResponse;
import com.jobtracking.model.Reminder;
import com.jobtracking.model.User;
import com.jobtracking.repository.ReminderRepository;
import com.jobtracking.repository.UserRepository;
import com.jobtracking.service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reminders")
@CrossOrigin(origins = "*")
public class ReminderController {

    private final ReminderRepository reminderRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;

    public ReminderController(ReminderRepository reminderRepository,
                              GoogleCalendarService googleCalendarService,
                              UserRepository userRepository) {
        this.reminderRepository = reminderRepository;
        this.googleCalendarService = googleCalendarService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Reminder> getAllReminders() {
        return reminderRepository.findAll();
    }

    /**
     * Validate that interview end time is not earlier than start time
     * Prevents invalid interview durations
     */
    private void validateInterviewTimes(Reminder reminder) {
        if ("INTERVIEW".equals(reminder.getKind()) &&
                reminder.getStartTime() != null &&
                reminder.getEndTime() != null &&
                !reminder.getEndTime().isEmpty()) {

            try {
                LocalTime startTime = LocalTime.parse(reminder.getStartTime(),
                        DateTimeFormatter.ofPattern("HH:mm"));
                LocalTime endTime = LocalTime.parse(reminder.getEndTime(),
                        DateTimeFormatter.ofPattern("HH:mm"));

                // If end time is before start time, auto-correct it
                if (endTime.isBefore(startTime)) {
                    LocalTime correctedEndTime = startTime.plusHours(1);
                    reminder.setEndTime(correctedEndTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                    System.out.println("[Time Validation] ⚠ Corrected interview end time from " +
                            endTime + " to " + correctedEndTime);
                }
            } catch (Exception e) {
                System.err.println("[Time Validation] Error parsing interview times: " + e.getMessage());
            }
        }
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

    @PostMapping
    public Reminder createReminder(@RequestBody Reminder reminder) {
        User currentUser = getCurrentUser();
        reminder.setUser(currentUser);

        if (reminder.getCreatedAt() == null) {
            reminder.setCreatedAt(LocalDate.now());
        }

        // Validate interview times
        validateInterviewTimes(reminder);

        // Save reminder first
        Reminder savedReminder = reminderRepository.save(reminder);

        // Auto-sync to Google Calendar if enabled
        if (currentUser.isGoogleCalendarEnabled() && currentUser.getGoogleAccessToken() != null) {
            try {
                String googleCalendarEventId = googleCalendarService.createCalendarEvent(
                        savedReminder,
                        currentUser.getGoogleAccessToken(),
                        currentUser.getTimezone()
                );

                // Only store calendar event ID if one was created (FOLLOWUP reminders return null)
                if (googleCalendarEventId != null) {
                    savedReminder.setGoogleCalendarEventId(googleCalendarEventId);
                    System.out.println("[Google Calendar] ✓ Auto-synced reminder to Google Calendar: " + googleCalendarEventId);
                }

                // Create a Google Task ONLY for FOLLOWUP events (not DEADLINE)
                System.out.println("[Google Tasks] Checking if should create task...");
                System.out.println("[Google Tasks] - Kind: " + savedReminder.getKind());
                System.out.println("[Google Tasks] - isGoogleTasksEnabled: " + currentUser.isGoogleTasksEnabled());
                System.out.println("[Google Tasks] - Has token: " + (currentUser.getGoogleAccessToken() != null));

                if ("FOLLOWUP".equals(savedReminder.getKind()) && currentUser.isGoogleTasksEnabled()) {
                    System.out.println("[Google Tasks] ✓ Conditions met - creating task...");
                    try {
                        String googleTaskId = googleCalendarService.createGoogleTask(
                                savedReminder,
                                currentUser.getGoogleAccessToken()
                        );
                        savedReminder.setGoogleTaskId(googleTaskId);
                        System.out.println("[Google Tasks] ✓ Auto-synced followup to Google Tasks: " + googleTaskId);
                    } catch (Exception e) {
                        System.err.println("[Google Tasks] ✗ Failed to create Google Task: " + e.getMessage());
                        e.printStackTrace();
                        // Don't fail the request - reminder is still created locally
                    }
                } else {
                    System.out.println("[Google Tasks] ⊘ Skipping task creation - conditions not met");
                    if (!"FOLLOWUP".equals(savedReminder.getKind())) {
                        System.out.println("[Google Tasks]   - Kind is not FOLLOWUP (it's: " + savedReminder.getKind() + ")");
                    }
                    if (!currentUser.isGoogleTasksEnabled()) {
                        System.out.println("[Google Tasks]   - Google Tasks not enabled for user");
                    }
                }

                savedReminder = reminderRepository.save(savedReminder);
            } catch (Exception e) {
                System.err.println("[Google Calendar] ✗ Failed to sync reminder to Google Calendar: " + e.getMessage());
                e.printStackTrace();
                // Don't fail the request - reminder is still created locally
            }
        } else if (!currentUser.isGoogleCalendarEnabled()) {
            System.out.println("[Google Calendar] ⚠ Google integration not enabled for user: " + currentUser.getEmail());
        }

        return savedReminder;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reminder> getReminder(@PathVariable UUID id) {
        return reminderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Reminder> updateReminder(@PathVariable UUID id, @RequestBody Reminder details) {
        return reminderRepository.findById(id)
                .map(reminder -> {
                    User currentUser = getCurrentUser();

                    reminder.setTitle(details.getTitle());
                    reminder.setNotes(details.getNotes());
                    reminder.setColor(details.getColor());
                    reminder.setTriggerAt(details.getTriggerAt());

                    if ("INTERVIEW".equals(reminder.getKind())) {
                        reminder.setEndDate(details.getEndDate());
                        reminder.setStartTime(details.getStartTime());
                        reminder.setEndTime(details.getEndTime());
                        reminder.setLocation(details.getLocation());
                        reminder.setMeetingLink(details.getMeetingLink());
                    }

                    if ("FOLLOWUP".equals(reminder.getKind())) {
                        reminder.setStartTime(details.getStartTime());
                    }

                    // Validate interview times
                    validateInterviewTimes(reminder);

                    Reminder updatedReminder = reminderRepository.save(reminder);

                    // Auto-sync update to Google Calendar if enabled and event ID exists
                    if (currentUser.isGoogleCalendarEnabled() &&
                            currentUser.getGoogleAccessToken() != null &&
                            updatedReminder.getGoogleCalendarEventId() != null) {
                        try {
                            googleCalendarService.updateCalendarEvent(
                                    updatedReminder.getGoogleCalendarEventId(),
                                    updatedReminder,
                                    currentUser.getGoogleAccessToken(),
                                    currentUser.getTimezone()
                            );
                            System.out.println("[Google Calendar] ✓ Auto-synced reminder update to Google Calendar: " + updatedReminder.getGoogleCalendarEventId());
                        } catch (Exception e) {
                            System.err.println("[Google Calendar] ✗ Failed to sync reminder update to Google Calendar: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the request - reminder is still updated locally
                        }
                    }

                    // Auto-sync update to Google Task if enabled and task ID exists
                    // Only for FOLLOWUP reminders
                    if (currentUser.isGoogleTasksEnabled() &&
                            currentUser.getGoogleAccessToken() != null &&
                            updatedReminder.getGoogleTaskId() != null &&
                            "FOLLOWUP".equals(updatedReminder.getKind())) {
                        try {
                            googleCalendarService.updateGoogleTask(
                                    updatedReminder.getGoogleTaskId(),
                                    updatedReminder,
                                    currentUser.getGoogleAccessToken()
                            );
                            System.out.println("[Google Tasks] ✓ Auto-synced followup update to Google Tasks: " + updatedReminder.getGoogleTaskId());
                        } catch (Exception e) {
                            System.err.println("[Google Tasks] ✗ Failed to sync followup update to Google Tasks: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the request - reminder is still updated locally
                        }
                    }

                    return ResponseEntity.ok(updatedReminder);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReminder(@PathVariable UUID id) {
        return reminderRepository.findById(id)
                .map(reminder -> {
                    User currentUser = getCurrentUser();

                    // Auto-sync deletion to Google Calendar if enabled and event ID exists
                    if (currentUser.isGoogleCalendarEnabled() &&
                            currentUser.getGoogleAccessToken() != null &&
                            reminder.getGoogleCalendarEventId() != null) {
                        try {
                            googleCalendarService.deleteCalendarEvent(
                                    reminder.getGoogleCalendarEventId(),
                                    currentUser.getGoogleAccessToken()
                            );
                            System.out.println("[Google Calendar] ✓ Auto-synced reminder deletion to Google Calendar: " + reminder.getGoogleCalendarEventId());
                        } catch (Exception e) {
                            System.err.println("[Google Calendar] ✗ Failed to sync reminder deletion to Google Calendar: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the request - reminder is still deleted locally
                        }
                    }

                    // Auto-sync deletion to Google Task if enabled and task ID exists
                    // Only for FOLLOWUP reminders
                    if (currentUser.isGoogleTasksEnabled() &&
                            currentUser.getGoogleAccessToken() != null &&
                            reminder.getGoogleTaskId() != null &&
                            "FOLLOWUP".equals(reminder.getKind())) {
                        try {
                            googleCalendarService.deleteGoogleTask(
                                    reminder.getGoogleTaskId(),
                                    currentUser.getGoogleAccessToken()
                            );
                            System.out.println("[Google Tasks] ✓ Auto-synced followup deletion to Google Tasks: " + reminder.getGoogleTaskId());
                        } catch (Exception e) {
                            System.err.println("[Google Tasks] ✗ Failed to sync followup deletion to Google Tasks: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the request - reminder is still deleted locally
                        }
                    }

                    reminderRepository.delete(reminder);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a Google Calendar event from a reminder
     * POST /api/reminders/{id}/calendar
     * Body: { "accessToken": "ya29.a0..." }
     */
    @PostMapping("/{id}/calendar")
    public ResponseEntity<CalendarEventResponse> createCalendarEvent(
            @PathVariable UUID id,
            @RequestBody CalendarEventRequest request) {

        try {
            // Find the reminder
            Reminder reminder = reminderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Reminder not found"));

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