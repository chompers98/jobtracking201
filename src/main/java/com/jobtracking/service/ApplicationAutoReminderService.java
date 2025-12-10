package com.jobtracking.service;

import com.jobtracking.model.Application;
import com.jobtracking.model.Reminder;
import com.jobtracking.model.User;
import com.jobtracking.repository.ReminderRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/**
 * Service to automatically create and manage reminders from application data
 * Handles auto-creation of DEADLINE and INTERVIEW reminders when applications are created/updated
 */
@Service
public class ApplicationAutoReminderService {

    private final ReminderRepository reminderRepository;
    private final GoogleCalendarService googleCalendarService;

    public ApplicationAutoReminderService(ReminderRepository reminderRepository,
                                          GoogleCalendarService googleCalendarService) {
        this.reminderRepository = reminderRepository;
        this.googleCalendarService = googleCalendarService;
    }

    /**
     * Automatically create reminders for an application based on its deadlines and interview times
     * Called when an application is created or updated
     *
     * @param application The application that was created/updated
     * @param user The user who owns the application
     */
    public void generateRemindersForApplication(Application application, User user) {
        // Create DEADLINE reminder if application has a deadline
        if (application.getDeadlineAt() != null) {
            Reminder deadlineReminder = createOrUpdateDeadlineReminder(application, user);
            syncReminderToGoogleCalendar(deadlineReminder, user);
        }

        // Create INTERVIEW reminder if application has an interview time
        if (application.getInterviewAt() != null) {
            Reminder interviewReminder = createOrUpdateInterviewReminder(application, user);
            syncReminderToGoogleCalendar(interviewReminder, user);
        }
    }

    /**
     * Create or update a DEADLINE reminder for an application
     */
    private Reminder createOrUpdateDeadlineReminder(Application application, User user) {
        // Check if reminder already exists for this application
        var existingReminders = reminderRepository.findByApplicationId(application.getId());
        Reminder deadlineReminder = existingReminders.stream()
                .filter(r -> "DEADLINE".equals(r.getKind()))
                .findFirst()
                .orElse(null);

        if (deadlineReminder == null) {
            // Create new reminder
            deadlineReminder = new Reminder();
            deadlineReminder.setApplicationId(application.getId());
            deadlineReminder.setKind("DEADLINE");
            deadlineReminder.setCreatedAt(LocalDate.now());
        }

        // Update reminder details
        deadlineReminder.setUser(user);
        deadlineReminder.setTitle(String.format("%s - %s", application.getCompany(), application.getTitle()));
        deadlineReminder.setTriggerAt(application.getDeadlineAt());
        deadlineReminder.setColor("blue"); // Default color for deadlines
        deadlineReminder.setNotes(String.format("Application deadline for %s position at %s",
                application.getTitle(), application.getCompany()));

        return reminderRepository.save(deadlineReminder);
    }

    /**
     * Create or update an INTERVIEW reminder for an application
     */
    private Reminder createOrUpdateInterviewReminder(Application application, User user) {
        // Check if reminder already exists for this application
        var existingReminders = reminderRepository.findByApplicationId(application.getId());
        Reminder interviewReminder = existingReminders.stream()
                .filter(r -> "INTERVIEW".equals(r.getKind()))
                .findFirst()
                .orElse(null);

        if (interviewReminder == null) {
            // Create new reminder
            interviewReminder = new Reminder();
            interviewReminder.setApplicationId(application.getId());
            interviewReminder.setKind("INTERVIEW");
            interviewReminder.setCreatedAt(LocalDate.now());
        }

        // Update reminder details
        interviewReminder.setUser(user);
        interviewReminder.setTitle(String.format("Interview - %s at %s", application.getTitle(), application.getCompany()));
        interviewReminder.setTriggerAt(application.getInterviewAt().toLocalDate());
        interviewReminder.setEndDate(application.getInterviewAt().toLocalDate());

        // Extract time from LocalDateTime if available
        if (application.getInterviewAt() != null) {
            String timeString = String.format("%02d:%02d",
                    application.getInterviewAt().getHour(),
                    application.getInterviewAt().getMinute());
            interviewReminder.setStartTime(timeString);
            interviewReminder.setEndTime(String.format("%02d:%02d",
                    application.getInterviewAt().plusHours(1).getHour(),
                    application.getInterviewAt().plusHours(1).getMinute()));
        }

        interviewReminder.setColor("orange"); // Default color for interviews
        interviewReminder.setNotes(String.format("Interview for %s position at %s",
                application.getTitle(), application.getCompany()));

        return reminderRepository.save(interviewReminder);
    }

    /**
     * Remove reminders for an application (called when application is deleted)
     */
    public void removeRemindersForApplication(java.util.UUID applicationId) {
        var reminders = reminderRepository.findByApplicationId(applicationId);
        reminderRepository.deleteAll(reminders);
    }

    /**
     * Sync a reminder to Google Calendar if user has Google integration enabled
     */
    private void syncReminderToGoogleCalendar(Reminder reminder, User user) {
        try {
            if (user.isGoogleCalendarEnabled() && user.getGoogleAccessToken() != null) {
                String googleCalendarEventId = googleCalendarService.createCalendarEvent(
                        reminder,
                        user.getGoogleAccessToken(),
                        user.getTimezone()
                );
                reminder.setGoogleCalendarEventId(googleCalendarEventId);
                reminderRepository.save(reminder);
                System.out.println("[Google Calendar] ✓ Synced auto-reminder to Google Calendar: " + googleCalendarEventId);
            } else {
                System.out.println("[Google Calendar] ⚠ Google integration not enabled or no access token for user: " + user.getEmail());
            }
        } catch (Exception e) {
            System.err.println("[Google Calendar] ✗ Failed to sync auto-reminder: " + e.getMessage());
            e.printStackTrace();
            // Don't fail - reminder is still created locally
        }
    }
}