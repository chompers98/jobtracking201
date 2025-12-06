package com.jobtracking.dto;

/**
 * Response DTO for Google Calendar event creation
 */
public class CalendarEventResponse {

    private boolean success;
    private String calendarLink;
    private String message;

    public CalendarEventResponse() {}

    public CalendarEventResponse(boolean success, String calendarLink, String message) {
        this.success = success;
        this.calendarLink = calendarLink;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCalendarLink() {
        return calendarLink;
    }

    public void setCalendarLink(String calendarLink) {
        this.calendarLink = calendarLink;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}