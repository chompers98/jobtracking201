package com.jobtracking.service;

import com.jobtracking.dto.AuthResponse;
import com.jobtracking.dto.LoginRequest;
import com.jobtracking.dto.RegisterRequest;
import com.jobtracking.model.User;
import com.jobtracking.repository.UserRepository;
import com.jobtracking.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) throws Exception {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email already registered");
        }

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new Exception("Username already taken");
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");

        User savedUser = userRepository.save(user);

        // Generate JWT tokens
        String jwt = jwtTokenProvider.generateTokenFromUsername(savedUser.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getUsername());

        long expiresAt = System.currentTimeMillis() + jwtTokenProvider.getExpirationTimeMs();
        long refreshExpiresAt = System.currentTimeMillis() + jwtTokenProvider.getRefreshExpirationTimeMs();

        return new AuthResponse(
                jwt,
                refreshToken,
                expiresAt,
                refreshExpiresAt,
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    public AuthResponse login(LoginRequest request) throws Exception {
        // Authenticate the user using email
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Get the user
            Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
            if (!userOptional.isPresent()) {
                throw new Exception("User not found");
            }

            User user = userOptional.get();

            // Generate JWT tokens
            String jwt = jwtTokenProvider.generateToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

            long expiresAt = System.currentTimeMillis() + jwtTokenProvider.getExpirationTimeMs();
            long refreshExpiresAt = System.currentTimeMillis() + jwtTokenProvider.getRefreshExpirationTimeMs();

            return new AuthResponse(
                    jwt,
                    refreshToken,
                    expiresAt,
                    refreshExpiresAt,
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole()
            );
        } catch (Exception e) {
            throw new Exception("Invalid email or password");
        }
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
}