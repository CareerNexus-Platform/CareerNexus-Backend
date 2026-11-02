package com.CareerNexus_Backend.CareerNexus.controller;

import com.CareerNexus_Backend.CareerNexus.dto.*;
import com.CareerNexus_Backend.CareerNexus.model.CodingAssessment;
import com.CareerNexus_Backend.CareerNexus.model.CodingAssessmentResult;
import com.CareerNexus_Backend.CareerNexus.repository.CodingAssessmentRepository;
import com.CareerNexus_Backend.CareerNexus.service.CodingAssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coding-exam")
@CrossOrigin(origins = "*")
public class CodingAssessmentController {

    @Autowired
    private CodingAssessmentService codingAssessmentService;

    @Autowired
    private CodingAssessmentRepository assessmentRepository;

    @PostMapping("/tpo/create")
    public ResponseEntity<?> createCodingAssessment(
            Authentication authentication,
            @RequestBody CodingAssessmentRequestDto requestDto) {
        try {
            String tpoId = authentication.getName();
            CodingAssessment created = codingAssessmentService.createCodingAssessment(requestDto, tpoId);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating assessment: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<?> getResults(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(codingAssessmentService.getRoundResults(id, authentication.getName()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<CodingAssessment>> getAllAssessments() {
        return ResponseEntity.ok(assessmentRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/student/dashboard")
    public ResponseEntity<List<StudentAssessmentDashboardDto>> getStudentDashboard(Authentication authentication) {
        String studentId = authentication.getName();
        return ResponseEntity.ok(codingAssessmentService.getStudentAssessmentDashboard(studentId));
    }

    @GetMapping("/{id}/start")
    public ResponseEntity<?> startAssessment(@PathVariable Long id, Authentication authentication) {
        try {
            String studentId = authentication.getName();
            StudentCodingAssessmentDto dto = codingAssessmentService.getStudentCodingAssessment(id, studentId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching assessment: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitAssessment(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody CodeSubmissionRequestDto submissionDto) {
        try {
            String studentId = authentication.getName();
            CodingAssessmentResult result = codingAssessmentService.submitCodingAssessment(id, submissionDto, studentId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error submitting assessment: " + e.getMessage());
        }
    }
}
