package com.CareerNexus_Backend.CareerNexus.controller;

import com.CareerNexus_Backend.CareerNexus.model.User;
import com.CareerNexus_Backend.CareerNexus.service.EmailService;
import com.CareerNexus_Backend.CareerNexus.service.UserAuthServiceImplementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private UserAuthServiceImplementation userAuthService;

    @Autowired
    private EmailService emailService;

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHAR_UPPER = CHAR_LOWER.toUpperCase();
    private static final String NUMBER = "0123456789";
    private static final String DATA_FOR_RANDOM_STRING = CHAR_LOWER + CHAR_UPPER + NUMBER;
    private static SecureRandom random = new SecureRandom();

    @PostMapping("/create-user")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<?> createUser(@RequestBody User userRequest) {
        try {
            // 1. Generate a random 10-character password
            String rawPassword = generateRandomPassword(10);
            
            // 2. Prepare user for registration
            String originalPassword = userRequest.getPassword(); // Should be null or empty from frontend
            userRequest.setPassword(rawPassword);
            
            // 3. Register user (hashes the password)
            User registeredUser = userAuthService.registerUser(userRequest);
            
            // 4. Send welcome email with RAW password
            emailService.sendWelcomeEmail(
                    registeredUser.getEmail(),
                    registeredUser.getUserId(),
                    rawPassword,
                    registeredUser.getRole()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User created successfully and welcome email sent.",
                "userId", registeredUser.getUserId()
            ));

        } catch (Exception e) {
            logger.error("Failed to create user: {}. Error: {}", userRequest.getUserId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String generateRandomPassword(int length) {
        if (length < 1) throw new IllegalArgumentException();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int rndCharAt = random.nextInt(DATA_FOR_RANDOM_STRING.length());
            char rndChar = DATA_FOR_RANDOM_STRING.charAt(rndCharAt);
            sb.append(rndChar);
        }
        return sb.toString();
    }
}
