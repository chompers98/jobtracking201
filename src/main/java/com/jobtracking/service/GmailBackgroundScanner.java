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
import com.jobtracking.repository.ApplicationRepository;
import com.jobtracking.repository.EmailSyncStateRepository;
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

    public GmailBackgroundScanner(ApplicationRepository applicationRepository,
                                  EmailParserService emailParserService,
                                  EmailSyncStateRepository emailSyncStateRepository) {
        this.applicationRepository = applicationRepository;
        this.emailParserService = emailParserService;
        this.emailSyncStateRepository = emailSyncStateRepository;
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
     * using lastProcessedInternalDate from EmailSyncState.
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scanInbox() {
        // Don't scan if not connected/enabled
        Boolean enabled = (Boolean) AuthController.googleIntegration.get("enabled");
        Boolean connected = (Boolean) AuthController.googleIntegration.get("connected");

        if (enabled == null || !enabled || connected == null || !connected) {
            return;
        }

        try {
            // 1) Load or initialize sync state (id = 1)
            EmailSyncState state = emailSyncStateRepository.findById(1L).orElseGet(() -> {
                EmailSyncState s = new EmailSyncState();
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

            System.out.println("Scanning Gmail...");
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
                    updateApplication(sender, subject, newStatus);
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

    private void updateApplication(String sender, String subject, String newStatus) {
        List<Application> allApps = applicationRepository.findAll();
        String senderLower = sender == null ? "" : sender.toLowerCase();
        String subjectLower = subject == null ? "" : subject.toLowerCase();

        for (Application app : allApps) {
            if (app.getCompany() == null) continue;

            String company = app.getCompany().toLowerCase();
            if (senderLower.contains(company) || subjectLower.contains(company)) {
                if (!newStatus.equals(app.getStatus())) {
                    System.out.println("Updating " + app.getCompany() + " status to " + newStatus);
                    String existingNotes = app.getNotes() == null ? "" : app.getNotes();
                    String autoNote = "\n[Auto-Update " + LocalDate.now() + "] Status: " + newStatus;
                    app.setStatus(newStatus);
                    app.setNotes(existingNotes + autoNote);
                    applicationRepository.save(app);
                }
                return; // stop after first matching application
            }
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