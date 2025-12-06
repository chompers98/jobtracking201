package com.jobtracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * LLM-powered email parsing using Claude API
 * Used as fallback when regex extraction fails or needs higher accuracy
 */
@Service
public class LLMEmailParserService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract company and job title using Claude API
     * Returns array: [company, jobTitle]
     */
    public String[] extractCompanyAndTitle(String sender, String subject, String body) {
        // If API key not configured, return nulls
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("[LLM Parser] API key not configured, skipping LLM extraction");
            return new String[]{null, null};
        }

        try {
            String prompt = buildExtractionPrompt(sender, subject, body);
            String response = callClaudeAPI(prompt);
            return parseResponse(response);

        } catch (Exception e) {
            System.err.println("[LLM Parser] Error: " + e.getMessage());
            return new String[]{null, null};
        }
    }

    /**
     * Build prompt for Claude API
     */
    private String buildExtractionPrompt(String sender, String subject, String body) {
        return String.format(
                "Extract the company name and job title from this job application email. " +
                        "Return ONLY a JSON object with format: {\"company\": \"...\", \"title\": \"...\"}. " +
                        "If you cannot determine either field, use \"Unknown\" for that field.\n\n" +
                        "Email Details:\n" +
                        "From: %s\n" +
                        "Subject: %s\n" +
                        "Body: %s\n\n" +
                        "JSON Response:",
                sender != null ? sender : "N/A",
                subject != null ? subject : "N/A",
                body != null ? body.substring(0, Math.min(500, body.length())) : "N/A"
        );
    }

    /**
     * Call Claude API
     */
    private String callClaudeAPI(String prompt) throws Exception {
        URL url = new URL(ANTHROPIC_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Set headers
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);

        // Build request body
        String requestBody = String.format(
                "{" +
                        "\"model\": \"%s\"," +
                        "\"max_tokens\": 200," +
                        "\"messages\": [{" +
                        "\"role\": \"user\"," +
                        "\"content\": %s" +
                        "}]" +
                        "}",
                MODEL,
                objectMapper.writeValueAsString(prompt)
        );

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("API returned status: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        return response.toString();
    }

    /**
     * Parse Claude API response to extract company and title
     */
    private String[] parseResponse(String apiResponse) {
        try {
            JsonNode root = objectMapper.readTree(apiResponse);

            // Get the text content from response
            JsonNode contentArray = root.path("content");
            if (contentArray.isArray() && contentArray.size() > 0) {
                String text = contentArray.get(0).path("text").asText();

                // Extract JSON from text (Claude might wrap it in markdown)
                text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

                // Parse the JSON
                JsonNode extracted = objectMapper.readTree(text);
                String company = extracted.path("company").asText("Unknown");
                String title = extracted.path("title").asText("Unknown");

                // Clean up "Unknown" values
                if (company.equalsIgnoreCase("unknown") || company.equalsIgnoreCase("n/a")) {
                    company = null;
                }
                if (title.equalsIgnoreCase("unknown") || title.equalsIgnoreCase("n/a")) {
                    title = null;
                }

                System.out.println("[LLM Parser] Extracted - Company: " + company + ", Title: " + title);
                return new String[]{company, title};
            }

        } catch (Exception e) {
            System.err.println("[LLM Parser] Failed to parse response: " + e.getMessage());
        }

        return new String[]{null, null};
    }

    /**
     * Check if API key is configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}