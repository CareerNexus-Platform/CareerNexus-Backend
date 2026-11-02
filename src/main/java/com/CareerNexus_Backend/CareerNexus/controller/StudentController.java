package com.CareerNexus_Backend.CareerNexus.controller;

import com.CareerNexus_Backend.CareerNexus.dto.ChangePasswordDTO;
import com.CareerNexus_Backend.CareerNexus.dto.RecruiterDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.dto.StudentDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.service.RecruiterService;
import com.CareerNexus_Backend.CareerNexus.service.StudentServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")  // ← added leading slash
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    @Autowired
    private StudentServices studentServices;

    @Autowired
    private RecruiterService recruiterService;

    // ── Create or Update Profile ──────────────────────────────────────────────

    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudentDetailsDTO> profile(
            @RequestPart("data") StudentDetailsDTO studentDetailsDTO,
            @RequestPart(value = "imageFile", required = false) MultipartFile img,
            Authentication authentication) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentServices
                .createOrUpdateProfile(studentDetailsDTO, authentication.getName(), img));
    }

    @PostMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentDetailsDTO> profileJson(
            @RequestBody StudentDetailsDTO studentDetailsDTO,
            Authentication authentication) throws Exception {
        return ResponseEntity.ok(studentServices.createOrUpdateProfile(
                studentDetailsDTO, authentication.getName(), null));
    }

    // ── Get Profile ───────────────────────────────────────────────────────────

    @GetMapping("/profile")
    public ResponseEntity<StudentDetailsDTO> getStudentProfile(
            Authentication authentication) throws Exception {
        return ResponseEntity.ok(studentServices.getProfileData(authentication.getName()));
    }

    // ── Companies Visited ─────────────────────────────────────────────────────

    @GetMapping("/companies-visited")
    public List<RecruiterDetailsDTO> recentCompanies() {
        return recruiterService.getAllCompanies();
    }

    // ── Change Password ───────────────────────────────────────────────────────

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            // userId taken from JWT token

            @RequestBody ChangePasswordDTO request) {

        String userId = authentication.getName();
        return studentServices.changePassword(userId, request);
    }
}
