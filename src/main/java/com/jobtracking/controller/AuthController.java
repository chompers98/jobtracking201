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
import com.jobtracking.dto.AuthResponse;
import com.jobtracking.dto.LoginRequest;
import com.jobtracking.dto.RegisterRequest;
import com.jobtracking.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
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
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    
    // User integration settings store (Mock for now, ideally DB)
    public static final Map<String, Object> googleIntegration = new HashMap<>();
    
    static {
        googleIntegration.put("enabled", false);
        googleIntegration.put("gmailEnabled", true);
        googleIntegration.put("calendarEnabled", true);
        googleIntegration.put("email", "");
        googleIntegration.put("connected", false);
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

    public void googleAuthorize(HttpServletResponse response) throws Exception {
        GoogleAuthorizationCodeFlow flow = getFlow();
        String authorizationUrl = flow.newAuthorizationUrl()
                .setRedirectUri(CALLBACK_URI)
                .build();
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/oauth/google/callback")
    public void googleCallback(@RequestParam("code") String code, HttpServletResponse response) throws Exception {
        GoogleAuthorizationCodeFlow flow = getFlow();
        GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(CALLBACK_URI)
                .execute();
        
        // Store credentials using the DataStoreFactory configured in getFlow()
        // In this case, it stores in 'tokens' directory
        flow.createAndStoreCredential(tokenResponse, "user");
        
        // Update integration status
        googleIntegration.put("connected", true);
        googleIntegration.put("enabled", true);
        
        // Redirect back to frontend
        response.sendRedirect("/integrations.html");
    }
    
    @GetMapping("/user/integrations")
    public Map<String, Object> getUserIntegrations() {
        Map<String, Object> result = new HashMap<>();
        result.put("google", googleIntegration);
        result.put("openai", Map.of("enabled", false)); // Placeholder
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
        // Clear token (simplified logic)
        try {
            java.io.File tokenDir = new java.io.File("tokens");
            if (tokenDir.exists()) {
                for (java.io.File file : tokenDir.listFiles()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

