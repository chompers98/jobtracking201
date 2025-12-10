package com.jobtracking.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.model.Task;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.jobtracking.model.Reminder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Google Calendar Integration Service
 * Section 1.2: Calendar Integration - Google Calendar API (OAuth2)
 */
@Service
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "JobTracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    /**
     * Create a Google Calendar event from a reminder
     * Note: FOLLOWUP reminders do NOT create calendar events - only Google Tasks
     *
     * @param reminder The reminder to create an event for
     * @param accessToken User's Google OAuth access token
     * @param userTimezone User's timezone (e.g., "America/Los_Angeles")
     * @return The calendar event ID (null for FOLLOWUP reminders)
     */
    public String createCalendarEvent(Reminder reminder, String accessToken, String userTimezone)
            throws IOException, GeneralSecurityException {

        // FOLLOWUP reminders should NOT create calendar events - only Google Tasks
        if ("FOLLOWUP".equals(reminder.getKind())) {
            System.out.println("[Google Calendar] ⊘ Skipping calendar event for FOLLOWUP - will create Google Task instead");
            return null;
        }

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credentials from access token
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        // Build Calendar service
        Calendar service = new Calendar.Builder(
                httpTransport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Create event
        Event event = new Event()
                .setSummary(reminder.getTitle() != null ? reminder.getTitle() : "Job Application Reminder")
                .setDescription(buildEventDescription(reminder));

        // Set event time based on reminder type
        if ("INTERVIEW".equals(reminder.getKind()) && reminder.getStartTime() != null) {
            // Interview with specific time
            setInterviewDateTime(event, reminder, userTimezone);
        } else if ("DEADLINE".equals(reminder.getKind()) && reminder.getStartTime() != null) {
            // Application deadline with specific time: create 0-minute event at that time
            setDeadlineDateTime(event, reminder, userTimezone);
        } else {
            // All-day event for deadlines without specific time
            setAllDayEvent(event, reminder);
        }

        // Set location if available
        if (reminder.getLocation() != null && !reminder.getLocation().isEmpty()) {
            event.setLocation(reminder.getLocation());
        }

        // Insert event into primary calendar
        Event createdEvent = service.events()
                .insert("primary", event)
                .execute();

        System.out.println("[Google Calendar] ✓ Created calendar event ID: " + createdEvent.getId());
        return createdEvent.getId();
    }

    /**
     * Set interview date/time for the event
     */
    private void setInterviewDateTime(Event event, Reminder reminder, String userTimezone) {
        try {
            // Parse start time
            LocalTime startTime = LocalTime.parse(reminder.getStartTime(),
                    DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime startDateTime = reminder.getTriggerAt().atTime(startTime);

            // Parse end time (or default to start time + 1 hour)
            LocalTime endTime;
            if (reminder.getEndTime() != null && !reminder.getEndTime().isEmpty()) {
                endTime = LocalTime.parse(reminder.getEndTime(),
                        DateTimeFormatter.ofPattern("HH:mm"));
            } else {
                endTime = startTime.plusHours(1);
            }

            LocalDateTime endDateTime = (reminder.getEndDate() != null ?
                    reminder.getEndDate() : reminder.getTriggerAt()).atTime(endTime);

            // Convert to user's timezone
            ZoneId zoneId = ZoneId.of(userTimezone != null ? userTimezone : "America/Los_Angeles");

            long startMillis = startDateTime
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli();

            long endMillis = endDateTime
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli();

            // Set start and end time with user's timezone
            event.setStart(new EventDateTime()
                    .setDateTime(new DateTime(new Date(startMillis)))
                    .setTimeZone(userTimezone != null ? userTimezone : "America/Los_Angeles"));

            event.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(new Date(endMillis)))
                    .setTimeZone(userTimezone != null ? userTimezone : "America/Los_Angeles"));

        } catch (Exception e) {
            System.err.println("Error parsing interview time, defaulting to all-day event: " + e.getMessage());
            setAllDayEvent(event, reminder);
        }
    }

    /**
     * Set application deadline date/time as a 0-minute event at the specified time
     */
    private void setDeadlineDateTime(Event event, Reminder reminder, String userTimezone) {
        try {
            // Parse deadline time
            LocalTime deadlineTime = LocalTime.parse(reminder.getStartTime(),
                    DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime deadlineDateTime = reminder.getTriggerAt().atTime(deadlineTime);

            // Convert to user's timezone
            ZoneId zoneId = ZoneId.of(userTimezone != null ? userTimezone : "America/Los_Angeles");

            long deadlineMillis = deadlineDateTime
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli();

            // Set start and end to the SAME time (0-minute event)
            event.setStart(new EventDateTime()
                    .setDateTime(new DateTime(new Date(deadlineMillis)))
                    .setTimeZone(userTimezone != null ? userTimezone : "America/Los_Angeles"));

            event.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(new Date(deadlineMillis)))
                    .setTimeZone(userTimezone != null ? userTimezone : "America/Los_Angeles"));

        } catch (Exception e) {
            System.err.println("Error parsing deadline time, defaulting to all-day event: " + e.getMessage());
            setAllDayEvent(event, reminder);
        }
    }

    /**
     * Set all-day event (for deadlines and follow-ups)
     */
    private void setAllDayEvent(Event event, Reminder reminder) {
        DateTime date = new DateTime(reminder.getTriggerAt().toString());
        event.setStart(new EventDateTime().setDate(date));
        event.setEnd(new EventDateTime().setDate(date));
    }

    /**
     * Build event description from reminder
     */
    private String buildEventDescription(Reminder reminder) {
        StringBuilder description = new StringBuilder();

        if (reminder.getNotes() != null && !reminder.getNotes().isEmpty()) {
            description.append(reminder.getNotes()).append("\n\n");
        }

        description.append("Reminder Type: ").append(reminder.getKind()).append("\n");

        if ("INTERVIEW".equals(reminder.getKind())) {
            if (reminder.getMeetingLink() != null && !reminder.getMeetingLink().isEmpty()) {
                description.append("\nMeeting Link: ").append(reminder.getMeetingLink());
            }
        }

        description.append("\n\n---\n");
        description.append("Created by JobTracker");

        return description.toString();
    }

    /**
     * Validate that the access token is valid
     */
    public boolean validateAccessToken(String accessToken) {
        try {
            GoogleCredentials credentials = GoogleCredentials.create(
                    new AccessToken(accessToken, null)
            );
            // If we can create credentials, token format is valid
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Update an existing Google Calendar event
     *
     * @param googleCalendarEventId The ID of the event to update
     * @param reminder The reminder with updated data
     * @param accessToken User's Google OAuth access token
     * @param userTimezone User's timezone
     * @return The HTML link to the updated calendar event
     */
    public String updateCalendarEvent(String googleCalendarEventId, Reminder reminder, String accessToken, String userTimezone)
            throws IOException, GeneralSecurityException {

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credentials from access token
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        // Build Calendar service
        Calendar service = new Calendar.Builder(
                httpTransport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Get existing event
        Event event = service.events().get("primary", googleCalendarEventId).execute();

        // Update event details
        event.setSummary(reminder.getTitle() != null ? reminder.getTitle() : "Job Application Reminder")
                .setDescription(buildEventDescription(reminder));

        // Update time based on reminder type
        if ("INTERVIEW".equals(reminder.getKind()) && reminder.getStartTime() != null) {
            setInterviewDateTime(event, reminder, userTimezone);
        } else if ("DEADLINE".equals(reminder.getKind()) && reminder.getStartTime() != null) {
            // Application deadline with specific time: create 0-minute event at that time
            setDeadlineDateTime(event, reminder, userTimezone);
        } else {
            setAllDayEvent(event, reminder);
        }

        // Update location if available
        if (reminder.getLocation() != null && !reminder.getLocation().isEmpty()) {
            event.setLocation(reminder.getLocation());
        } else {
            event.setLocation(null);
        }

        // Update the event
        Event updatedEvent = service.events()
                .update("primary", googleCalendarEventId, event)
                .execute();

        System.out.println("[Google Calendar] ✓ Updated calendar event ID: " + updatedEvent.getId());
        return updatedEvent.getId();
    }

    /**
     * Delete a Google Calendar event
     *
     * @param googleCalendarEventId The ID of the event to delete
     * @param accessToken User's Google OAuth access token
     */
    public void deleteCalendarEvent(String googleCalendarEventId, String accessToken)
            throws IOException, GeneralSecurityException {

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credentials from access token
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        // Build Calendar service
        Calendar service = new Calendar.Builder(
                httpTransport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Delete the event
        service.events().delete("primary", googleCalendarEventId).execute();

        System.out.println("[Google Calendar] ✓ Deleted calendar event ID: " + googleCalendarEventId);
    }

    /**
     * Create a Google Task for a followup reminder
     *
     * @param reminder The reminder to create a task for
     * @param accessToken User's Google OAuth access token
     * @return The task ID
     */
    public String createGoogleTask(Reminder reminder, String accessToken)
            throws IOException, GeneralSecurityException {

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credentials from access token
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        // Build Tasks service
        Tasks service = new Tasks.Builder(
                httpTransport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Create task
        Task task = new Task()
                .setTitle(reminder.getTitle() != null ? reminder.getTitle() : "Job Application Task")
                .setNotes(buildTaskDescription(reminder));

        // Set due date (Google Tasks uses RFC 3339 format)
        if (reminder.getTriggerAt() != null) {
            // Convert LocalDate to RFC 3339 format: 2025-12-14T00:00:00Z
            String dueDate = reminder.getTriggerAt().toString() + "T00:00:00Z";
            task.setDue(dueDate);
            System.out.println("[Google Tasks] Due date set to: " + dueDate);
        }

        // Insert task into @default tasklist
        Task createdTask = service.tasks()
                .insert("@default", task)
                .execute();

        System.out.println("[Google Tasks] ✓ Created task ID: " + createdTask.getId());
        return createdTask.getId();
    }

    /**
     * Update an existing Google Task
     *
     * @param googleTaskId The ID of the task to update
     * @param reminder The reminder with updated data
     * @param accessToken User's Google OAuth access token
     * @return The task ID
     */
    public String updateGoogleTask(String googleTaskId, Reminder reminder, String accessToken)
            throws IOException, GeneralSecurityException {

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credentials from access token
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        // Build Tasks service
        Tasks service = new Tasks.Builder(
                httpTransport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Get existing task
        Task task = service.tasks().get("@default", googleTaskId).execute();

        // Update task details
        task.setTitle(reminder.getTitle() != null ? reminder.getTitle() : "Job Application Task")
                .setNotes(buildTaskDescription(reminder));

        // Update due date (Google Tasks uses YYYY-MM-DD format)
        if (reminder.getTriggerAt() != null) {
            String dueDate = reminder.getTriggerAt().toString(); // LocalDate.toString() returns YYYY-MM-DD
            task.setDue(dueDate);
            System.out.println("[Google Tasks] Updated due date to: " + dueDate);
        }

        // Update the task
        Task updatedTask = service.tasks()
                .update("@default", googleTaskId, task)
                .execute();

        System.out.println("[Google Tasks] ✓ Updated task ID: " + updatedTask.getId());
        return updatedTask.getId();
    }

    /**
     * Delete a Google Task
     *
     * @param googleTaskId The ID of the task to delete
     * @param accessToken User's Google OAuth access token
     */
    public void deleteGoogleTask(String googleTaskId, String accessToken)
            throws IOException, GeneralSecurityException {

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credentials from access token
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        // Build Tasks service
        Tasks service = new Tasks.Builder(
                httpTransport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Delete the task
        service.tasks().delete("@default", googleTaskId).execute();

        System.out.println("[Google Tasks] ✓ Deleted task ID: " + googleTaskId);
    }

    /**
     * Build task description from reminder
     */
    private String buildTaskDescription(Reminder reminder) {
        StringBuilder description = new StringBuilder();

        if (reminder.getNotes() != null && !reminder.getNotes().isEmpty()) {
            description.append(reminder.getNotes()).append("\n\n");
        }

        description.append("Reminder Type: ").append(reminder.getKind()).append("\n");

        if (reminder.getApplicationId() != null) {
            description.append("Application ID: ").append(reminder.getApplicationId()).append("\n");
        }

        description.append("\n---\n");
        description.append("Created by JobTracker");

        return description.toString();
    }
}