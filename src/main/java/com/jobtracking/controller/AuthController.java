package com.jobtracking.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.tasks.TasksScopes;
import com.jobtracking.dto.AuthResponse;
import com.jobtracking.dto.LoginRequest;
import com.jobtracking.dto.RegisterRequest;
import com.jobtracking.model.User;
import com.jobtracking.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private AuthenticationService authenticationService;

    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String CALLBACK_URI = "http://localhost:8080/api/oauth/google/callback";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(
            GmailScopes.GMAIL_READONLY,      // Read Gmail (for future email tracking)
            CalendarScopes.CALENDAR,          // Read/write Google Calendar (for event syncing)
            "https://www.googleapis.com/auth/tasks"  // Read/write Google Tasks (for followup reminders)
    );

    // User integration settings store (Mock for now, ideally DB)
    public static final Map<String, Object> googleIntegration = new HashMap<>();

    static {
        googleIntegration.put("enabled", false);
        googleIntegration.put("gmailEnabled", true);
        googleIntegration.put("calendarEnabled", true);
        googleIntegration.put("email", "");
        googleIntegration.put("connected", false);
        googleIntegration.put("connectedUserId", null); // Track which user connected Google
    }

    // ==================== USER AUTHENTICATION ENDPOINTS ====================

    /**
     * POST /api/auth/register
     * Register a new user account
     * Request: {email, password, username}
     * Response: 201 Created with AuthResponse containing JWT token
     */
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        try {
            AuthResponse authResponse = authenticationService.register(registerRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
    }

    /**
     * POST /api/auth/login
     * Authenticate a user and return JWT tokens
     * Request: {email, password}
     * Response: {jwt, expires_at, refreshToken, ...}
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            AuthResponse authResponse = authenticationService.login(loginRequest);
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * POST /api/auth/logout
     * Logout the authenticated user
     * Authorization header required with Bearer token
     * Response: 200 OK
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout() {
        SecurityContextHolder.clearContext();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    // ==================== GOOGLE OAUTH ENDPOINTS ====================

    @GetMapping("/oauth/google/authorize")
    public void googleAuthorize(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        try {
            // Get the authenticated user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Check if authenticated AND not anonymous
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                System.out.println("[Google OAuth] ✗ Not authenticated - redirecting to login");
                response.sendRedirect("/login.html?error=not_authenticated");
                return;
            }

            String email = auth.getName();
            System.out.println("[Google OAuth] Starting OAuth flow for user: " + email);

            // Verify user exists in database
            User user = authenticationService.getUserRepository().findByEmail(email).orElse(null);
            if (user == null) {
                System.out.println("[Google OAuth] ✗ User not found in database: " + email);
                response.sendRedirect("/login.html?error=user_not_found");
                return;
            }

            // Store user email in session so callback can find it
            request.getSession().setAttribute("googleOAuthEmail", email);
            System.out.println("[Google OAuth] ✓ Stored email in session: " + email);

            GoogleAuthorizationCodeFlow flow = getFlow();
            String authorizationUrl = flow.newAuthorizationUrl()
                    .setRedirectUri(CALLBACK_URI)
                    .setAccessType("offline")  // Request refresh token
                    .build();

            // Add prompt parameter manually (not available as method in library)
            authorizationUrl = authorizationUrl + "&prompt=consent";

            response.sendRedirect(authorizationUrl);

        } catch (Exception e) {
            System.out.println("[Google OAuth] ✗ Authorize error: " + e.getMessage());
            try {
                response.sendRedirect("/login.html?error=oauth_error");
            } catch (Exception e2) {
                // Ignore redirect errors
            }
        }
    }

    @GetMapping("/oauth/google/callback")
    public void googleCallback(@RequestParam("code") String code, jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        try {
            GoogleAuthorizationCodeFlow flow = getFlow();
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(CALLBACK_URI)
                    .execute();

            flow.createAndStoreCredential(tokenResponse, "user");
            System.out.println("[Google OAuth] Token response received");

            // Try to get email from session first
            String email = (String) request.getSession().getAttribute("googleOAuthEmail");

            // Fallback: try SecurityContext
            if (email == null || email.isEmpty()) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    email = auth.getName();
                }
            }

            if (email != null && !email.isEmpty()) {
                User user = authenticationService.getUserRepository().findByEmail(email).orElse(null);
                if (user != null) {
                    user.setGoogleAccessToken(tokenResponse.getAccessToken());
                    if (tokenResponse.getRefreshToken() != null) {
                        user.setGoogleRefreshToken(tokenResponse.getRefreshToken());
                    }
                    user.setGoogleCalendarEnabled(true);
                    user.setGoogleTasksEnabled(true);
                    user.setGoogleGmailEnabled(true);
                    // Update timestamp to mark user as active/logged in
                    user.setUpdatedAt(java.time.LocalDateTime.now());
                    authenticationService.getUserRepository().save(user);
                    authenticationService.getUserRepository().flush();

                    // Verify save
                    User verify = authenticationService.getUserRepository().findByEmail(email).orElse(null);
                    if (verify != null && verify.isGoogleCalendarEnabled()) {
                        System.out.println("[Google OAuth] ✓ Tokens saved for " + email);
                        
                        // Update static map so GmailBackgroundScanner knows we're connected
                        googleIntegration.put("enabled", true);
                        googleIntegration.put("connected", true);
                        googleIntegration.put("connectedUserId", user.getId());
                        googleIntegration.put("email", email);
                        googleIntegration.put("gmailEnabled", true);
                        System.out.println("[Google OAuth] ✓ Updated googleIntegration map for Gmail scanning");
                    } else {
                        System.out.println("[Google OAuth] ✗ Save verification failed for " + email);
                    }
                } else {
                    System.out.println("[Google OAuth] ✗ User not found: " + email);
                }
            } else {
                System.out.println("[Google OAuth] ✗ No email found in session or context");
            }

        } catch (Exception e) {
            System.out.println("[Google OAuth] ✗ Error: " + e.getMessage());
            // Don't rethrow - just log and continue
        }

        // Always redirect back, even if something failed
        try {
            response.sendRedirect("/integrations.html");
        } catch (Exception e) {
            System.out.println("[Google OAuth] ✗ Failed to redirect: " + e.getMessage());
        }
    }

    @GetMapping("/user/integrations")
    public Map<String, Object> getUserIntegrations() {
        Map<String, Object> result = new HashMap<>();

        // Default google settings
        Map<String, Object> googleSettings = new HashMap<>();
        googleSettings.put("enabled", false);
        googleSettings.put("connected", false);
        googleSettings.put("gmailEnabled", true);
        googleSettings.put("calendarEnabled", true);
        googleSettings.put("email", "");

        // Load from database if user is authenticated
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                User user = authenticationService.getUserRepository().findByEmail(email).orElse(null);

                if (user != null) {
                    // Check if user has Google integration enabled AND tokens saved
                    boolean hasTokens = user.getGoogleAccessToken() != null && !user.getGoogleAccessToken().isEmpty();
                    boolean isEnabled = user.isGoogleCalendarEnabled();
                    boolean isConnected = isEnabled && hasTokens;

                    googleSettings.put("enabled", isEnabled);
                    googleSettings.put("connected", isConnected);
                    googleSettings.put("email", email);
                    googleSettings.put("calendarEnabled", isEnabled);
                }
            }
        } catch (Exception e) {
            System.out.println("[Integrations] Error loading user settings: " + e.getMessage());
            // Return default settings on error
        }

        result.put("google", googleSettings);
        result.put("openai", Map.of("enabled", false));
        return result;
    }

    @PutMapping("/user/integrations")
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateUserIntegrations(@RequestBody Map<String, Object> updates) {
        if (updates.containsKey("google")) {
            Map<String, Object> googleUpdates = (Map<String, Object>) updates.get("google");
            googleIntegration.putAll(googleUpdates);
        }
        return getUserIntegrations();
    }

    @DeleteMapping("/user/integrations/google")
    public ResponseEntity<?> disconnectGoogle() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                User user = authenticationService.getUserRepository().findByEmail(email).orElse(null);
                if (user != null) {
                    user.setGoogleCalendarEnabled(false);
                    user.setGoogleTasksEnabled(false);
                    user.setGoogleGmailEnabled(false);
                    user.setGoogleAccessToken(null);
                    user.setGoogleRefreshToken(null);
                    authenticationService.getUserRepository().save(user);
                    authenticationService.getUserRepository().flush();
                    System.out.println("[Google OAuth] ✓ Disconnected " + email);
                }
            }
        } catch (Exception e) {
            System.out.println("[Google OAuth] ✗ Disconnect error: " + e.getMessage());
            // Don't fail the request even if disconnect has issues
        }

        // Try to clear token files
        try {
            java.io.File tokenDir = new java.io.File("tokens");
            if (tokenDir.exists() && tokenDir.isDirectory()) {
                for (java.io.File file : tokenDir.listFiles()) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Google OAuth] ✗ Could not clear token files: " + e.getMessage());
            // Non-critical error
        }

        googleIntegration.put("connected", false);
        googleIntegration.put("enabled", false);
        googleIntegration.put("email", "");

        return ResponseEntity.ok().build();
    }

    private GoogleAuthorizationCodeFlow getFlow() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        InputStream in = AuthController.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            // Fallback for running from IDE vs Jar
            in = new FileInputStream("src/main/resources" + CREDENTIALS_FILE_PATH);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        return new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();
    }

}