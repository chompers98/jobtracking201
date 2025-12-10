package com.jobtracking.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import com.jobtracking.controller.AuthController;
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
import java.util.UUID;

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

    private Gmail getGmailService() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        InputStream in = GmailBackgroundScanner.class.getResourceAsStream("/credentials.json");
        if (in == null) {
            in = new FileInputStream("src/main/resources/credentials.json");
        }

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();

        // Load stored credential for "user"
        Credential credential = flow.loadCredential("user");

        if (credential != null &&
                (credential.getRefreshToken() != null ||
                        credential.getExpiresInSeconds() == null ||
                        credential.getExpiresInSeconds() > 60)) {
            return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
        return null;
    }

    /**
     * Background email scanner.
     * Runs every 60 seconds, but only processes NEW emails
     * using lastProcessedInternalDate from EmailSyncState (per user).
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scanInbox() {
        System.out.println("[GmailScanner] ===== SCAN TRIGGERED AT " + java.time.LocalDateTime.now() + " =====");
        
        // Don't scan if not connected/enabled
        Boolean enabled = (Boolean) AuthController.googleIntegration.get("enabled");
        Boolean connected = (Boolean) AuthController.googleIntegration.get("connected");
        UUID connectedUserId = (UUID) AuthController.googleIntegration.get("connectedUserId");

        if (enabled == null || !enabled || connected == null || !connected) {
            System.out.println("Not connected");
            return;
        }

        // Get the user who connected Google
        User user = null;
        if (connectedUserId != null) {
            user = userRepository.findById(connectedUserId).orElse(null);
        }
        
        // If no connected user, try to get the first user as fallback
        if (user == null) {
            List<User> allUsers = userRepository.findAll();
            if (!allUsers.isEmpty()) {
                user = allUsers.get(0);
                System.out.println("[GmailScanner] No connected user ID found, using first user: " + user.getUsername());
            } else {
                System.out.println("[GmailScanner] No users found in database, skipping email scan");
                return;
            }
        }

        final User scanUser = user; // Make effectively final for lambda use

        try {
            // 1) Load or initialize sync state for this user
            EmailSyncState state = emailSyncStateRepository.findByUser_Id(scanUser.getId())
                    .orElseGet(() -> {
                        EmailSyncState s = new EmailSyncState(scanUser);
                        s.setLastProcessedInternalDate(0L);
                        return emailSyncStateRepository.save(s);
                    });

            long lastProcessed = state.getLastProcessedInternalDate() == null
                    ? 0L
                    : state.getLastProcessedInternalDate();
            long maxSeen = lastProcessed;

            // 2) Gmail service
            Gmail service = getGmailService();
            if (service == null) {
                System.out.println("Gmail service not authenticated. Please connect via Integrations page.");
                return;
            }

            System.out.println("[GmailScanner] Scanning Gmail for user: " + scanUser.getUsername() + "...");
            String query = "is:unread subject:(application OR job OR interview OR offer OR rejected)";
            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(10L)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages == null || messages.isEmpty()) {
                return;
            }

            // 3) Process only NEW messages based on internalDate as our "UID"
            for (Message message : messages) {
                Message fullMsg = service.users()
                        .messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                Long internalDate = fullMsg.getInternalDate();
                if (internalDate == null) {
                    internalDate = 0L;
                }

                // ✅ Skip messages we've already processed
                if (internalDate <= lastProcessed) {
                    continue;
                }

                String subject = getHeader(fullMsg, "Subject");
                String sender = getHeader(fullMsg, "From");
                String body = getBody(fullMsg);

                System.out.println("Processing Email: " + subject);

                String newStatus = emailParserService.determineStatus(subject, body);

                if (newStatus != null) {
                    // If status is "APPLIED", try to create new application
                    if ("APPLIED".equals(newStatus)) {
                        createApplicationFromEmail(subject, body, sender, scanUser);
                    } else {
                        // For other statuses, update existing application
                        updateExistingApplication(sender, subject, body, newStatus, scanUser);
                    }
                }

                // Track the newest internalDate we've seen this run
                if (internalDate > maxSeen) {
                    maxSeen = internalDate;
                }
            }

            // 4) Update lastProcessedInternalDate if we saw newer emails
            if (maxSeen > lastProcessed) {
                state.setLastProcessedInternalDate(maxSeen);
                emailSyncStateRepository.save(state);
            }
        } catch (Exception e) {
            // Log for debugging instead of completely silent fail
            e.printStackTrace();
        }
    }

    /**
     * Create a new application from email (when status = APPLIED)
     * Uses regex + LLM fallback for company/title extraction
     */
    private void createApplicationFromEmail(String subject, String body, String sender, User user) {
        try {
            // Step 1: Extract company and job title using regex
            String company = emailParserService.extractCompany(sender, subject, body);
            String jobTitle = emailParserService.extractJobTitle(subject, body);

            // Step 2: If regex fails, try LLM enhancement
            if (("Unknown Company".equals(company) || "Unknown Position".equals(jobTitle))
                    && llmEmailParserService.isConfigured()) {

                System.out.println("[GmailScanner] Regex extraction incomplete, trying LLM...");
                String[] llmResult = llmEmailParserService.extractCompanyAndTitle(sender, subject, body);

                if (llmResult[0] != null && !"Unknown Company".equals(company)) {
                    company = llmResult[0];
                }
                if (llmResult[1] != null && !"Unknown Position".equals(jobTitle)) {
                    jobTitle = llmResult[1];
                }
            }

            // Step 3: Check for duplicate (company + title combo) for THIS USER
            Application existing = applicationRepository.findByUser_IdAndCompanyAndTitle(
                    user.getId(), company, jobTitle);

            if (existing != null) {
                System.out.println("[GmailScanner] Application already exists for " +
                        company + " - " + jobTitle + " (user: " + user.getUsername() + "), skipping creation");
                return;
            }

            // Step 4: Create new application for this user
            Application application = new Application();
            application.setUser(user);
            application.setCompany(company);
            application.setTitle(jobTitle);
            application.setStatus("APPLIED");
            application.setCreatedAt(LocalDate.now());
            application.setAppliedAt(LocalDate.now());
            application.setNotes(
                    "[Auto-created from email on " + LocalDate.now() + "]\n" +
                            "Sender: " + sender + "\n" +
                            "Subject: " + subject
            );

            applicationRepository.save(application);
            System.out.println("[GmailScanner] ✅ Created new application for user " + 
                    user.getUsername() + ": " + company + " - " + jobTitle);

        } catch (Exception e) {
            System.err.println("[GmailScanner] Error creating application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update existing application based on email (for INTERVIEW, OFFER, REJECTED)
     * Uses company + title matching with fallback to company-only
     * Only updates applications belonging to the specified user
     */
    private void updateExistingApplication(String sender, String subject, String body, String newStatus, User user) {
        try {
            // Extract company and job title
            String company = emailParserService.extractCompany(sender, subject, body);
            String jobTitle = emailParserService.extractJobTitle(subject, body);

            // Try LLM if regex fails
            if (("Unknown Company".equals(company) || "Unknown Position".equals(jobTitle))
                    && llmEmailParserService.isConfigured()) {

                String[] llmResult = llmEmailParserService.extractCompanyAndTitle(sender, subject, body);
                if (llmResult[0] != null) company = llmResult[0];
                if (llmResult[1] != null) jobTitle = llmResult[1];
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
                    System.out.println("[GmailScanner] ⚠️ No exact match found, updating first application for " + 
                            company + " (user: " + user.getUsername() + ")");
                }
            }

            // Update if found
            if (application != null) {
                if (!newStatus.equals(application.getStatus())) {
                    System.out.println("[GmailScanner] Updating " + application.getCompany() +
                            " - " + application.getTitle() + " status to " + newStatus + 
                            " (user: " + user.getUsername() + ")");

                    String existingNotes = application.getNotes() == null ? "" : application.getNotes();
                    String autoNote = "\n[Auto-Update " + LocalDate.now() + "] Status: " + newStatus;

                    application.setStatus(newStatus);
                    application.setNotes(existingNotes + autoNote);
                    applicationRepository.save(application);
                }
            } else {
                System.out.println("[GmailScanner] ⚠️ No matching application found for: " + company + 
                        " (user: " + user.getUsername() + ")");
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
        if (message.getPayload() == null) return "";
        return message.getSnippet() != null ? message.getSnippet() : "";
    }
}