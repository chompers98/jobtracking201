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
            "(?i)(application received|successfully submitted|application confirmation|thank you for applying)"
    );

    // Company extraction patterns
    private static final Pattern COMPANY_FROM_PATTERN = Pattern.compile(
            "(?i)(from|at|with|for|to)\\s+([A-Z][A-Za-z0-9\\.\\s&]+?)(?=\\s+(team|application|position|role|for|\\.|,|$))",
            Pattern.MULTILINE
    );

    // Job title extraction patterns
    private static final Pattern JOB_TITLE_PATTERN = Pattern.compile(
            "(?i)(position|role|job|opening|opportunity)[:\\s-]+([A-Za-z\\s]+?)(?=\\s+(at|with|position|role|$|\\.|,))",
            Pattern.MULTILINE
    );

    private static final Pattern DIRECT_TITLE_PATTERN = Pattern.compile(
            "(?i)(software engineer|senior engineer|junior engineer|product manager|data scientist|" +
                    "data engineer|frontend|backend|full stack|devops|sre|machine learning|" +
                    "ui/ux designer|ux designer|product designer|analyst|consultant|" +
                    "intern|internship|developer|programmer)",
            Pattern.CASE_INSENSITIVE
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

    /**
     * Extract company name from email
     * Priority: sender domain > subject/body patterns > fallback
     */
    public String extractCompany(String sender, String subject, String body) {
        String company = null;

        // Strategy 1: Extract from sender email domain
        if (sender != null && sender.contains("@")) {
            String domain = sender.substring(sender.indexOf("@") + 1);

            // Remove common email providers
            if (!domain.matches("(?i)(gmail|yahoo|hotmail|outlook|icloud|aol|mail)\\.com")) {
                // Extract company from domain (e.g., hr@google.com → Google)
                company = domain.split("\\.")[0];
                company = capitalizeWords(company);

                // If looks valid, return it
                if (company.length() > 2) {
                    return company;
                }
            }
        }

        // Strategy 2: Look for "from/at/with [Company]" patterns in subject/body
        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");
        Matcher matcher = COMPANY_FROM_PATTERN.matcher(content);

        if (matcher.find() && matcher.groupCount() >= 2) {
            company = matcher.group(2).trim();
            company = capitalizeWords(company);

            // Clean up common suffixes
            company = company.replaceAll("(?i)\\s+(team|recruiting|careers|jobs|hr)$", "");

            if (company.length() > 2) {
                return company;
            }
        }

        // Strategy 3: Fallback - look for known company patterns (could expand this)
        Pattern knownCompanies = Pattern.compile(
                "(?i)(google|amazon|microsoft|meta|facebook|apple|netflix|tesla|" +
                        "uber|lyft|airbnb|stripe|spotify|twitter|linkedin|salesforce|oracle|" +
                        "adobe|nvidia|intel|ibm|cisco|paypal|ebay|snap|pinterest)",
                Pattern.CASE_INSENSITIVE
        );

        matcher = knownCompanies.matcher(content);
        if (matcher.find()) {
            company = matcher.group(1);
            return capitalizeWords(company);
        }

        // Fallback
        return "Unknown Company";
    }

    /**
     * Extract job title from email
     */
    public String extractJobTitle(String subject, String body) {
        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");

        // Strategy 1: Look for "position: [Title]" or "role: [Title]"
        Matcher matcher = JOB_TITLE_PATTERN.matcher(content);
        if (matcher.find() && matcher.groupCount() >= 2) {
            String title = matcher.group(2).trim();
            title = capitalizeWords(title);

            if (title.length() > 3) {
                return title;
            }
        }

        // Strategy 2: Look for common job titles directly
        matcher = DIRECT_TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            String title = matcher.group(0);
            return capitalizeWords(title);
        }

        // Fallback
        return "Unknown Position";
    }

    /**
     * Capitalize first letter of each word
     */
    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;

        String[] words = text.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }
}