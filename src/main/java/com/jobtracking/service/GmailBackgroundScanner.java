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

import com.jobtracking.model.Application;
import com.jobtracking.repository.ApplicationRepository;
import com.jobtracking.controller.AuthController;
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
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    
    private final ApplicationRepository applicationRepository;
    private final EmailParserService emailParserService;

    public GmailBackgroundScanner(ApplicationRepository applicationRepository, EmailParserService emailParserService) {
        this.applicationRepository = applicationRepository;
        this.emailParserService = emailParserService;
    }

    private Gmail getGmailService() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        
        InputStream in = GmailBackgroundScanner.class.getResourceAsStream("/credentials.json");
        if (in == null) {
            in = new FileInputStream("src/main/resources/credentials.json");
        }
        
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();
        
        // Load stored credential for "user"
        Credential credential = flow.loadCredential("user");
        
        if (credential != null && (credential.getRefreshToken() != null || credential.getExpiresInSeconds() > 60)) {
            return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
        return null;
    }

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scanInbox() {
        // Don't scan if not connected/enabled
        Boolean enabled = (Boolean) AuthController.googleIntegration.get("enabled");
        Boolean connected = (Boolean) AuthController.googleIntegration.get("connected");
        
        if (enabled == null || !enabled || connected == null || !connected) {
            return;
        }

        try {
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

            for (Message message : messages) {
                Message fullMsg = service.users().messages().get("me", message.getId()).setFormat("full").execute();
                
                String subject = getHeader(fullMsg, "Subject");
                String sender = getHeader(fullMsg, "From");
                String body = getBody(fullMsg);

                System.out.println("Processing Email: " + subject);

                String newStatus = emailParserService.determineStatus(subject, body);

                if (newStatus != null) {
                    updateApplication(sender, subject, newStatus);
                }
            }
        } catch (Exception e) {
            // Silent fail to avoid spamming logs if credential file missing
        }
    }

    private void updateApplication(String sender, String subject, String newStatus) {
        List<Application> allApps = applicationRepository.findAll();
        for (Application app : allApps) {
            String company = app.getCompany().toLowerCase();
            if (sender.toLowerCase().contains(company) || subject.toLowerCase().contains(company)) {
                if (!newStatus.equals(app.getStatus())) {
                    System.out.println("Updating " + app.getCompany() + " status to " + newStatus);
                    app.setStatus(newStatus);
                    app.setNotes((app.getNotes() == null ? "" : app.getNotes()) + "\n[Auto-Update " + LocalDate.now() + "] Status: " + newStatus);
                    applicationRepository.save(app);
                }
                return;
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
