package com.jobtracking.service;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailParserService {

    private static final Pattern INTERVIEW_PATTERN = Pattern.compile(
        "(?i)(interview|schedule a time|availability|coding challenge|technical screen)"
    );
    private static final Pattern OFFER_PATTERN = Pattern.compile(
        "(?i)(offer letter|congratulations|pleased to offer|welcome to the team)"
    );
    private static final Pattern REJECT_PATTERN = Pattern.compile(
        "(?i)(thank you for your interest|unfortunately|not moving forward|pursue other candidates)"
    );
    private static final Pattern APPLIED_PATTERN = Pattern.compile(
        "(?i)(application received|successfully submitted|application confirmation)"
    );

    public String determineStatus(String subject, String body) {
        String content = (subject + " " + body).toLowerCase();
        
        // Priority: OFFER > REJECTED > INTERVIEW > APPLIED
        if (OFFER_PATTERN.matcher(content).find()) return "OFFER";
        if (REJECT_PATTERN.matcher(content).find()) return "REJECTED";
        if (INTERVIEW_PATTERN.matcher(content).find()) return "INTERVIEW";
        if (APPLIED_PATTERN.matcher(content).find()) return "APPLIED";
        
        return null; 
    }
}

