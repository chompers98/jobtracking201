package com.jobtracking.dto;

/**
 * Request DTO for creating Google Calendar events
 */
public class CalendarEventRequest {

    private Long reminderId;
    private String accessToken;

    public CalendarEventRequest() {}

    public CalendarEventRequest(Long reminderId, String accessToken) {
        this.reminderId = reminderId;
        this.accessToken = accessToken;
    }

    public Long getReminderId() {
        return reminderId;
    }

    public void setReminderId(Long reminderId) {
        this.reminderId = reminderId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}