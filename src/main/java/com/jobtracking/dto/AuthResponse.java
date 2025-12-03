package com.jobtracking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String jwt;
    private String refreshToken;
    private long expiresAt;
    private long refreshExpiresAt;
    private String username;
    private String email;
    private String role;
}
