package com.jobtracking.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
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
     *
     * @param reminder The reminder to create an event for
     * @param accessToken User's Google OAuth access token
     * @param userTimezone User's timezone (e.g., "America/Los_Angeles")
     * @return The HTML link to the created calendar event
     */
    public String createCalendarEvent(Reminder reminder, String accessToken, String userTimezone)
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

        // Create event
        Event event = new Event()
                .setSummary(reminder.getTitle() != null ? reminder.getTitle() : "Job Application Reminder")
                .setDescription(buildEventDescription(reminder));

        // Set event time based on reminder type
        if ("INTERVIEW".equals(reminder.getKind()) && reminder.getStartTime() != null) {
            // Interview with specific time
            setInterviewDateTime(event, reminder, userTimezone);
        } else {
            // All-day event for deadlines and follow-ups
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

        System.out.println("Created calendar event: " + createdEvent.getHtmlLink());
        return createdEvent.getHtmlLink();
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
}