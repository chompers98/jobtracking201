package com.jobtracking.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import com.jobtracking.model.Application;
import com.jobtracking.model.EmailSyncState;
import com.jobtracking.model.User;
import com.jobtracking.repository.ApplicationRepository;
import com.jobtracking.repository.EmailSyncStateRepository;
import com.jobtracking.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
public class GmailBackgroundScanner {

    private static final String APPLICATION_NAME = "Job Tracking App";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES =
            Collections.singletonList(GmailScopes.GMAIL_READONLY);

    private final ApplicationRepository applicationRepository;
    private final EmailParserService emailParserService;
    private final EmailSyncStateRepository emailSyncStateRepository;
    private final LLMEmailParserService llmEmailParserService;
    private final UserRepository userRepository;

    public GmailBackgroundScanner(ApplicationRepository applicationRepository,
                                  EmailParserService emailParserService,
                                  EmailSyncStateRepository emailSyncStateRepository,
                                  LLMEmailParserService llmEmailParserService,
                                  UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.emailParserService = emailParserService;
        this.emailSyncStateRepository = emailSyncStateRepository;
        this.llmEmailParserService = llmEmailParserService;
        this.userRepository = userRepository;
    }

    /**
     * Get Gmail service for a specific user using their stored OAuth tokens.
     * Always tries to refresh the token since access tokens expire after ~1 hour.
     */
    private Gmail getGmailServiceForUser(User user) throws Exception {
        if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isEmpty()) {
            System.out.println("[GmailScanner] No access token for user: " + user.getUsername());
            return null;
        }

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        // Load client secrets for token refresh
        InputStream in = GmailBackgroundScanner.class.getResourceAsStream("/credentials.json");
        if (in == null) {
            in = new FileInputStream("src/main/resources/credentials.json");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Create credential from user's stored tokens
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
                .setClientAuthentication(new com.google.api.client.auth.oauth2.ClientParametersAuthentication(
                        clientSecrets.getDetails().getClientId(),
                        clientSecrets.getDetails().getClientSecret()))
                .build();

        credential.setAccessToken(user.getGoogleAccessToken());
        if (user.getGoogleRefreshToken() != null) {
            credential.setRefreshToken(user.getGoogleRefreshToken());
        }

        // Always try to refresh the token (access tokens expire after ~1 hour)
        if (user.getGoogleRefreshToken() != null && !user.getGoogleRefreshToken().isEmpty()) {
            try {
                System.out.println("[GmailScanner] Refreshing access token for user: " + user.getUsername());
                credential.refreshToken();
                // Save new access token back to user
                String newToken = credential.getAccessToken();
                if (newToken != null && !newToken.equals(user.getGoogleAccessToken())) {
                    user.setGoogleAccessToken(newToken);
                    userRepository.save(user);
                    System.out.println("[GmailScanner] Token refreshed successfully for: " + user.getUsername());
                }
            } catch (Exception e) {
                // If refresh fails with 401, the refresh token is invalid - user needs to reconnect
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    System.err.println("[GmailScanner] ⚠ Invalid refresh token for " + user.getUsername() + " - user needs to reconnect Gmail integration");
                    // Clear invalid tokens so we don't keep trying
                    user.setGoogleAccessToken(null);
                    user.setGoogleRefreshToken(null);
                    user.setGoogleGmailEnabled(false);
                    userRepository.save(user);
                    return null; // Return null to skip scanning this user
                } else {
                    System.err.println("[GmailScanner] Failed to refresh token for " + user.getUsername() + ": " + e.getMessage());
                    // Continue with existing token - it might still work for a bit
                }
            }
        } else {
            System.out.println("[GmailScanner] No refresh token for user: " + user.getUsername() + " - user needs to reconnect Gmail");
            return null; // Can't proceed without refresh token
        }

        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Background email scanner.
     * Runs every 60 seconds, scans emails for users who are currently logged in (active within last 5 minutes).
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scanInbox() {
        System.out.println("[GmailScanner] ===== SCAN TRIGGERED AT " + java.time.LocalDateTime.now() + " =====");
        
        // Find all users with Gmail enabled and access tokens
        List<User> users = userRepository.findAll();
        
        // Only scan users who have been active in the last 5 minutes (currently logged in)
        java.time.LocalDateTime cutoffTime = java.time.LocalDateTime.now().minusMinutes(5);
        
        int scannedCount = 0;
        for (User user : users) {
            if (user.isGoogleGmailEnabled() && 
                user.getGoogleAccessToken() != null && 
                !user.getGoogleAccessToken().isEmpty()) {
                
                // Check if user has been active very recently (updatedAt within last 5 minutes)
                if (user.getUpdatedAt() != null && user.getUpdatedAt().isAfter(cutoffTime)) {
                    System.out.println("[GmailScanner] Scanning for currently logged-in user: " + user.getUsername() + " (" + user.getEmail() + ")");
                    scanInboxForUser(user);
                    scannedCount++;
                } else {
                    // Silently skip inactive users - they're not logged in
                    if (scannedCount == 0 && user.getUpdatedAt() != null) {
                        System.out.println("[GmailScanner] User " + user.getUsername() + " not currently logged in (last active: " + user.getUpdatedAt() + ")");
                    }
                }
            }
        }
        
        if (scannedCount == 0) {
            System.out.println("[GmailScanner] No currently logged-in users with Gmail enabled. Login and connect Gmail to scan emails.");
        }
    }

    /**
     * Scan inbox for a specific user
     */
    private void scanInboxForUser(User user) {
        try {
            // 1) Load or initialize sync state for this user
            EmailSyncState state = emailSyncStateRepository.findByUser_Id(user.getId())
                    .orElseGet(() -> {
                        EmailSyncState s = new EmailSyncState(user);
                        s.setLastProcessedInternalDate(0L);
                        return emailSyncStateRepository.save(s);
                    });

            long lastProcessed = state.getLastProcessedInternalDate() == null
                    ? 0L
                    : state.getLastProcessedInternalDate();
            long maxSeen = lastProcessed;
            
            System.out.println("[GmailScanner] Last processed date for " + user.getUsername() + ": " + lastProcessed);

            // 2) Get Gmail service for THIS user
            Gmail service = getGmailServiceForUser(user);
            if (service == null) {
                System.out.println("[GmailScanner] No valid Gmail tokens for user: " + user.getUsername());
                return;
            }

            System.out.println("[GmailScanner] Calling Gmail API for user: " + user.getUsername() + "...");
            // Query for unread emails matching job-related keywords in subject OR body
            // Gmail searches both subject and body when using OR without "subject:" prefix
            String query = "is:unread (application OR job OR interview OR offer OR rejected OR position OR role OR hiring)";
            System.out.println("[GmailScanner] Query: " + query);
            
            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(5L)  // Get top 5 most recent unread emails
                    .execute();

            System.out.println("[GmailScanner] Gmail API response received");
            
            List<Message> messages = response.getMessages();
            if (messages == null || messages.isEmpty()) {
                System.out.println("[GmailScanner] No matching unread emails for " + user.getUsername());
                return;
            }
            
            System.out.println("[GmailScanner] Found " + messages.size() + " unread emails matching query");

            // 3) Process ALL unread messages - if they're unread, they're new and should be processed
            // We'll track processed message IDs in application notes to avoid duplicates
            for (Message message : messages) {
                try {
                    String messageId = message.getId();
                    Message fullMsg = service.users()
                            .messages()
                            .get("me", messageId)
                            .setFormat("full")
                            .execute();

                    Long internalDate = fullMsg.getInternalDate();
                    if (internalDate == null) {
                        internalDate = 0L;
                    }

                    String subject = getHeader(fullMsg, "Subject");
                    String sender = getHeader(fullMsg, "From");
                    
                    // Check if we've already processed this message ID by checking application notes
                    boolean alreadyProcessed = checkIfMessageProcessed(messageId, user);
                    if (alreadyProcessed) {
                        System.out.println("[GmailScanner] Skipping already processed email (by message ID): " + subject);
                        continue;
                    }

                    // Process the email - it's unread so it's new
                    String body = getBody(fullMsg);
                    System.out.println("[" + user.getUsername() + "] Processing Email: " + subject + " (messageId: " + messageId + ", internalDate: " + internalDate + ")");

                    String newStatus = emailParserService.determineStatus(subject, body);
                    System.out.println("[" + user.getUsername() + "] Determined status: " + (newStatus != null ? newStatus : "none") + " for: " + subject);

                    if (newStatus != null) {
                        // For any status found, try to update existing application or create new one
                        System.out.println("[" + user.getUsername() + "] Status is " + newStatus + ", attempting to update or create application...");
                        updateOrCreateApplication(sender, subject, body, newStatus, user, messageId);
                    } else {
                        System.out.println("[" + user.getUsername() + "] ⚠ No status detected for email: " + subject);
                        System.out.println("[" + user.getUsername() + "] Email content preview (first 200 chars): " + 
                                (body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body));
                    }

                    // Track the newest internalDate we've seen this run
                    if (internalDate > maxSeen) {
                        maxSeen = internalDate;
                    }
                } catch (Exception e) {
                    System.err.println("[GmailScanner] Error processing individual email for user " + user.getUsername() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 4) Update lastProcessedInternalDate if we saw newer emails
            if (maxSeen > lastProcessed) {
                state.setLastProcessedInternalDate(maxSeen);
                emailSyncStateRepository.save(state);
            }
        } catch (Exception e) {
            System.err.println("[GmailScanner] Error scanning for user " + user.getUsername() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if a message ID has already been processed by looking in application notes
     */
    private boolean checkIfMessageProcessed(String messageId, User user) {
        List<Application> userApps = applicationRepository.findByUser_Id(user.getId());
        for (Application app : userApps) {
            if (app.getNotes() != null && app.getNotes().contains("[GmailMessageId:" + messageId + "]")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a new application from email with the specified status
     * Uses regex + LLM fallback for company/title extraction
     */
    private void createApplicationFromEmail(String subject, String body, String sender, User user, String messageId, String status) {
        try {
            // Step 1: Extract company and job title using regex
            String company = emailParserService.extractCompany(sender, subject, body);
            String jobTitle = emailParserService.extractJobTitle(subject, body);
            
            System.out.println("[GmailScanner] Extracted from email - Company: " + company + ", Title: " + jobTitle + 
                    " (Sender: " + sender + ", Subject: " + subject + ")");

            // Step 2: If regex fails, try LLM enhancement
            if (("Unknown Company".equals(company) || "Unknown Position".equals(jobTitle))
                    && llmEmailParserService.isConfigured()) {

                System.out.println("[GmailScanner] Regex extraction incomplete, trying LLM...");
                String[] llmResult = llmEmailParserService.extractCompanyAndTitle(sender, subject, body);

                // Use LLM result if it's available and not "Unknown"
                if (llmResult[0] != null && !llmResult[0].equals("Unknown Company") && !llmResult[0].isEmpty()) {
                    company = llmResult[0];
                    System.out.println("[GmailScanner] LLM extracted company: " + company);
                }
                if (llmResult[1] != null && !llmResult[1].equals("Unknown Position") && !llmResult[1].isEmpty()) {
                    jobTitle = llmResult[1];
                    System.out.println("[GmailScanner] LLM extracted title: " + jobTitle);
                }
            }

            // Step 3: Check for duplicate (company + title combo) for THIS USER
            // First try exact match
            Application existing = applicationRepository.findByUser_IdAndCompanyAndTitle(
                    user.getId(), company, jobTitle);

            // If no exact match, try case-insensitive check by searching user's applications
            if (existing == null) {
                List<Application> userApps = applicationRepository.findByUser_Id(user.getId());
                for (Application app : userApps) {
                    if (app.getCompany() != null && app.getTitle() != null &&
                        app.getCompany().equalsIgnoreCase(company) && 
                        app.getTitle().equalsIgnoreCase(jobTitle)) {
                        existing = app;
                        break;
                    }
                }
            }

            if (existing != null) {
                System.out.println("[GmailScanner] Application already exists for " +
                        company + " - " + jobTitle + " (user: " + user.getUsername() + "), skipping creation");
                return;
            }

            // Step 4: Validate we have at least a company name before creating
            if (company == null || company.equals("Unknown Company") || company.trim().isEmpty()) {
                System.out.println("[GmailScanner] ⚠ Cannot create application: company name could not be extracted. " +
                        "Sender: " + sender + ", Subject: " + subject);
                return;
            }

            // Step 5: Create new application for this user
            Application application = new Application();
            application.setUser(user);
            application.setCompany(company);
            // Use default title if extraction failed
            if (jobTitle == null || jobTitle.equals("Unknown Position") || jobTitle.trim().isEmpty()) {
                jobTitle = "Position Not Specified";
            }
            application.setTitle(jobTitle);
            application.setStatus(status != null ? status : "APPLIED");
            application.setCreatedAt(LocalDate.now());
            // Set appliedAt only if status is APPLIED, otherwise leave null or set appropriate date
            if ("APPLIED".equals(status)) {
                application.setAppliedAt(LocalDate.now());
            }
            application.setNotes(
                    "[Auto-created from email on " + LocalDate.now() + "] Status: " + status + "\n" +
                            "Sender: " + sender + "\n" +
                            "Subject: " + subject + "\n" +
                            "[GmailMessageId:" + messageId + "]"
            );

            applicationRepository.save(application);
            System.out.println("[GmailScanner] ✓ Successfully created new application for user " + 
                    user.getUsername() + ": " + company + " - " + jobTitle + " with status " + status + 
                    " (ID: " + application.getId() + ")");

        } catch (Exception e) {
            System.err.println("[GmailScanner] Error creating application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update existing application or create new one based on email status
     * For any status (APPLIED, INTERVIEW, OFFER, REJECTED):
     * - If matching application found: update its status
     * - If no matching application found: create new application with detected status
     */
    private void updateOrCreateApplication(String sender, String subject, String body, String newStatus, User user, String messageId) {
        try {
            // Extract company and job title
            String company = emailParserService.extractCompany(sender, subject, body);
            String jobTitle = emailParserService.extractJobTitle(subject, body);

            // Try LLM if regex fails
            if (("Unknown Company".equals(company) || "Unknown Position".equals(jobTitle))
                    && llmEmailParserService.isConfigured()) {

                System.out.println("[GmailScanner] Regex extraction incomplete for update, trying LLM...");
                String[] llmResult = llmEmailParserService.extractCompanyAndTitle(sender, subject, body);
                if (llmResult[0] != null && !llmResult[0].equals("Unknown Company") && !llmResult[0].isEmpty()) {
                    company = llmResult[0];
                    System.out.println("[GmailScanner] LLM extracted company: " + company);
                }
                if (llmResult[1] != null && !llmResult[1].equals("Unknown Position") && !llmResult[1].isEmpty()) {
                    jobTitle = llmResult[1];
                    System.out.println("[GmailScanner] LLM extracted title: " + jobTitle);
                }
            }

            Application application = null;

            // Strategy 1: Try exact match (user + company + title)
            if (jobTitle != null && !jobTitle.equals("Unknown Position")) {
                application = applicationRepository.findByUser_IdAndCompanyAndTitle(
                        user.getId(), company, jobTitle);
            }

            // Strategy 2: Fallback to any application from this company for this user
            if (application == null) {
                List<Application> companyApps = applicationRepository.findAllByUser_IdAndCompany(
                        user.getId(), company);
                if (!companyApps.isEmpty()) {
                    application = companyApps.get(0);
                    System.out.println("[GmailScanner] ?? No exact match found, updating first application for " + 
                            company + " (user: " + user.getUsername() + ")");
                }
            }

            // Update if found, otherwise create new application
            if (application != null) {
                // Update existing application
                if (!newStatus.equals(application.getStatus())) {
                    System.out.println("[GmailScanner] Updating " + application.getCompany() +
                            " - " + application.getTitle() + " status to " + newStatus + 
                            " (user: " + user.getUsername() + ")");

                    String existingNotes = application.getNotes() == null ? "" : application.getNotes();
                    // Check if we've already processed this message ID
                    if (!existingNotes.contains("[GmailMessageId:" + messageId + "]")) {
                        String autoNote = "\n[Auto-Update " + LocalDate.now() + "] Status: " + newStatus + "\n[GmailMessageId:" + messageId + "]";
                        application.setStatus(newStatus);
                        application.setNotes(existingNotes + autoNote);
                        applicationRepository.save(application);
                        System.out.println("[GmailScanner] ✓ Successfully updated application status to " + newStatus);
                    } else {
                        System.out.println("[GmailScanner] Already processed this message ID for application update, skipping");
                    }
                } else {
                    System.out.println("[GmailScanner] Application already has status " + newStatus + ", no update needed");
                }
            } else {
                // No matching application found - create new one with the detected status
                System.out.println("[GmailScanner] No matching application found for: " + company + 
                        " (user: " + user.getUsername() + "), creating new application with status: " + newStatus);
                createApplicationFromEmail(subject, body, sender, user, messageId, newStatus);
            }

        } catch (Exception e) {
            System.err.println("[GmailScanner] Error updating application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getHeader(Message message, String headerName) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) return "";
        return message.getPayload().getHeaders().stream()
                .filter(h -> h.getName().equalsIgnoreCase(headerName))
                .map(h -> h.getValue())
                .findFirst()
                .orElse("");
    }

    private String getBody(Message message) {
        if (message.getPayload() == null) {
            // Fallback to snippet if available
            return message.getSnippet() != null ? message.getSnippet() : "";
        }
        
        return extractTextFromPayload(message.getPayload());
    }
    
    /**
     * Recursively extract text from Gmail message payload
     * Handles both simple and multipart messages
     */
    private String extractTextFromPayload(com.google.api.services.gmail.model.MessagePart payload) {
        StringBuilder bodyText = new StringBuilder();
        
        // If this part has a body with data, decode and return it
        if (payload.getBody() != null && payload.getBody().getData() != null) {
            String mimeType = payload.getMimeType();
            if ("text/plain".equals(mimeType) || "text/html".equals(mimeType)) {
                try {
                    // Gmail API returns base64url encoded data
                    byte[] data = com.google.api.client.util.Base64.decodeBase64(
                        payload.getBody().getData().replace('-', '+').replace('_', '/'));
                    String text = new String(data, "UTF-8");
                    // For HTML, strip tags for better parsing
                    if ("text/html".equals(mimeType)) {
                        text = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
                    }
                    bodyText.append(text).append(" ");
                } catch (Exception e) {
                    // If decoding fails, continue to try other parts
                }
            }
        }
        
        // If this is a multipart message, recursively process each part
        if (payload.getParts() != null) {
            for (com.google.api.services.gmail.model.MessagePart part : payload.getParts()) {
                bodyText.append(extractTextFromPayload(part));
            }
        }
        
        return bodyText.toString().trim();
    }
}