package com.jobtracking.dto;

import java.util.UUID;

/**
 * Request DTO for creating Google Calendar events
 */
public class CalendarEventRequest {

    private UUID reminderId;
    private String accessToken;

    public CalendarEventRequest() {}

    public CalendarEventRequest(UUID reminderId, String accessToken) {
        this.reminderId = reminderId;
        this.accessToken = accessToken;
    }

    public UUID getReminderId() {
        return reminderId;
    }

    public void setReminderId(UUID reminderId) {
        this.reminderId = reminderId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}