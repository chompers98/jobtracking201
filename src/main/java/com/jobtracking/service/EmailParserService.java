package com.jobtracking.service;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailParserService {

    private static final Pattern INTERVIEW_PATTERN = Pattern.compile(
        "(?i)(interview|schedule a time|availability|coding challenge|technical screen|phone screen)"
    );

    private static final Pattern OFFER_PATTERN = Pattern.compile(
        "(?i)(offer letter|congratulations|pleased to offer|welcome to the team)"
    );

    private static final Pattern REJECT_PATTERN = Pattern.compile(
        "(?i)(we (have )?decided|unfortunately[, ]|not moving forward|pursue other candidates)"
    );

    private static final Pattern APPLIED_PATTERN = Pattern.compile(
    "(?i)(application (received|submitted|confirmation|successfully|complete)|thank you for applying|we (have )?received your application|your application has been|application received|applied (for|to)|confirmation of your application|received your application|has received your application)"
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
     * Priority: subject line > sender domain > body patterns > fallback
     */
    public String extractCompany(String sender, String subject, String body) {
        String company = null;

        // Strategy 1: Look for known companies in SUBJECT LINE first
        if (subject != null && !subject.trim().isEmpty()) {
            Pattern knownCompanies = Pattern.compile(
                    "(?i)\\b(google|amazon|microsoft|meta|facebook|apple|netflix|tesla|" +
                            "uber|lyft|airbnb|stripe|spotify|twitter|linkedin|salesforce|oracle|" +
                            "adobe|nvidia|intel|ibm|cisco|paypal|ebay|snap|pinterest|" +
                            "nutanix|qualcomm|vmware|red hat|mongo db|databricks|snowflake|palantir|" +
                            "goldman sachs|jpmorgan|morgan stanley|bank of america|wells fargo|" +
                            "mckinsey|bain|boston consulting|deloitte|pwc|ey|kpmg|" +
                            "accenture|capgemini|infosys|tcs|wipro)\\b",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = knownCompanies.matcher(subject);
            if (matcher.find()) {
                company = matcher.group(1);
                return capitalizeWords(company);
            }

            // Strategy 1b: Look for "from/at/with [Company]" patterns in subject
            matcher = COMPANY_FROM_PATTERN.matcher(subject);
            if (matcher.find() && matcher.groupCount() >= 2) {
                company = matcher.group(2).trim();
                company = capitalizeWords(company);
                // Clean up common suffixes
                company = company.replaceAll("(?i)\\s+(team|recruiting|careers|jobs|hr)$", "");
                if (company.length() > 2) {
                    return company;
                }
            }

            // Strategy 1c: Extract first capitalized word/phrase from subject (common pattern: "Company Name ...")
            // This catches companies like "Nutanix", "Qualcomm", etc. that aren't in the known list
            // Match capitalized words at the start before job-related keywords
            Pattern companyNamePattern = Pattern.compile(
                    "^\\s*\\b([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)*?)\\s+(?:Software|Senior|Junior|Product|Data|Frontend|Backend|Full\\s+Stack|DevOps|SRE|Machine\\s+Learning|UI/UX|UX|Product|Intern|Internship|Engineer|Engineering|Developer|Manager|Scientist|Designer|Analyst|Consultant|Specialist|Lead|Director|Coordinator|Associate|Assistant|Interview|Application|Offer|Rejected|Job|Position|Role|Opening)",
                    Pattern.CASE_INSENSITIVE
            );
            matcher = companyNamePattern.matcher(subject);
            if (matcher.find()) {
                company = matcher.group(1).trim();
                // Skip if it's a common word that's not a company (but allow longer names)
                if (company.length() > 2 && !company.matches("(?i)^(Your|The|This|Our|We|I|You|Hello|Hi|Thank)$")) {
                    return capitalizeWords(company);
                }
            }
            
            // Strategy 1d: More flexible - extract any capitalized word at start of subject
            // This catches companies that appear at the very beginning
            Pattern firstWordPattern = Pattern.compile(
                    "^\\s*\\b([A-Z][a-zA-Z]+)\\b",
                    Pattern.CASE_INSENSITIVE
            );
            matcher = firstWordPattern.matcher(subject);
            if (matcher.find()) {
                company = matcher.group(1).trim();
                // Only use if it looks like a company name (longer than 2 chars, capitalized)
                if (company.length() > 2 && Character.isUpperCase(company.charAt(0))) {
                    return capitalizeWords(company);
                }
            }
        }

        // Strategy 2: Extract from sender email domain (but only if subject didn't yield a result)
        if (company == null && sender != null && sender.contains("@")) {
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

        // Strategy 3: Look for "from/at/with [Company]" patterns in subject + body
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

        // Strategy 4: Fallback - look for known company patterns in full content
        Pattern knownCompanies = Pattern.compile(
                "(?i)\\b(google|amazon|microsoft|meta|facebook|apple|netflix|tesla|" +
                        "uber|lyft|airbnb|stripe|spotify|twitter|linkedin|salesforce|oracle|" +
                        "adobe|nvidia|intel|ibm|cisco|paypal|ebay|snap|pinterest|" +
                        "nutanix|qualcomm|vmware|red hat|mongo db|databricks|snowflake|palantir)\\b",
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
     * Priority: subject line > body patterns
     */
    public String extractJobTitle(String subject, String body) {
        // Strategy 1: Extract from subject line first (most reliable)
        if (subject != null && !subject.trim().isEmpty()) {
            // Pattern 1: "Company Job Title - Something" 
            // Example: "Microsoft Software Engineering Internship - Interview Invitation"
            // Extract everything after company name until dash or "at" or end
            // This is simpler and more reliable for multi-word titles
            Pattern companyTitlePattern = Pattern.compile(
                    "(?i)^\\s*\\b(?:google|amazon|microsoft|meta|facebook|apple|netflix|tesla|uber|lyft|airbnb|stripe|spotify|twitter|linkedin|salesforce|oracle|adobe|nvidia|intel|ibm|cisco|paypal|ebay|snap|pinterest|nutanix|qualcomm|vmware|[A-Z][a-zA-Z]+)\\s+(.+?)(?=\\s*-\\s*|\\s+at\\s+|$)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = companyTitlePattern.matcher(subject);
            if (matcher.find()) {
                String title = matcher.group(1).trim();
                // Clean up extra spaces
                title = title.replaceAll("\\s+", " ").trim();
                // Remove common trailing words that aren't part of the title
                // Use word boundaries to avoid removing words that are part of the title itself
                title = title.replaceAll("(?i)\\s+\\b(interview|invitation|application\\s+received|application\\s+confirmation|offer\\s+letter|rejected|job|position|role|opening|opportunity|received|confirmation).*$", "");
                title = title.trim();
                // Make sure we got something meaningful (not just a single word that might be a company name)
                // Allow short titles like "UX Designer" (10 chars) but not single words
                if (title.length() > 3 && !title.matches("^[A-Z][a-z]+$")) {
                    return capitalizeWords(title);
                }
            }

            // Pattern 1b: "Job Title at Company" or "Job Title - Company"
            Pattern titleAtCompanyPattern = Pattern.compile(
                    "([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+){0,3}?\\s+(?:Software|Senior|Junior|Product|Data|Frontend|Backend|Full\\s+Stack|DevOps|SRE|Machine\\s+Learning|UI/UX|UX|Product|Intern|Internship|Engineer|Engineering|Developer|Manager|Scientist|Designer|Analyst|Consultant|Specialist|Lead|Director|Coordinator|Associate|Assistant))\\s+(?:at|\\-)\\s+",
                    Pattern.CASE_INSENSITIVE
            );
            matcher = titleAtCompanyPattern.matcher(subject);
            if (matcher.find()) {
                String title = matcher.group(1).trim();
                return capitalizeWords(title);
            }

            // Pattern 2: Look for "Job Title at Company" in subject
            Pattern subjectPattern2 = Pattern.compile(
                    "([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+){1,4}?)\\s+(?:Internship|Engineer|Developer|Manager|Analyst|Designer|Consultant|Specialist|Lead|Director|Coordinator|Associate|Assistant)\\s+at\\s+",
                    Pattern.CASE_INSENSITIVE
            );
            matcher = subjectPattern2.matcher(subject);
            if (matcher.find()) {
                String title = matcher.group(0).replaceAll("\\s+at\\s+.*$", "").trim();
                return capitalizeWords(title);
            }

            // Pattern 3: Look for common job titles in subject
            matcher = DIRECT_TITLE_PATTERN.matcher(subject);
            if (matcher.find()) {
                // Try to get the full phrase including modifiers
                Pattern fullTitlePattern = Pattern.compile(
                        "(?i)\\b([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+)*?\\s+(?:software|senior|junior|product|data|frontend|backend|full\\s+stack|devops|sre|machine\\s+learning|ui/ux|ux|product|intern|internship|engineer|developer|manager|scientist|engineer|designer|analyst|consultant))",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher fullMatcher = fullTitlePattern.matcher(subject);
                if (fullMatcher.find()) {
                    return capitalizeWords(fullMatcher.group(1));
                }
                return capitalizeWords(matcher.group(0));
            }
        }

        // Strategy 2: Look in body for "position: [Title]" or "role: [Title]"
        if (body != null && !body.trim().isEmpty()) {
            String content = (subject != null ? subject : "") + " " + body;
            Matcher matcher = JOB_TITLE_PATTERN.matcher(content);
            if (matcher.find() && matcher.groupCount() >= 2) {
                String title = matcher.group(2).trim();
                title = capitalizeWords(title);

                if (title.length() > 3 && !title.toLowerCase().contains("question") && !title.toLowerCase().contains("team")) {
                    return title;
                }
            }

            // Strategy 3: Look for common job titles in full content
            matcher = DIRECT_TITLE_PATTERN.matcher(content);
            if (matcher.find()) {
                String title = matcher.group(0);
                // Avoid picking up random phrases
                if (!title.toLowerCase().contains("opportunity to ask") && 
                    !title.toLowerCase().contains("questions about")) {
                    return capitalizeWords(title);
                }
            }
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