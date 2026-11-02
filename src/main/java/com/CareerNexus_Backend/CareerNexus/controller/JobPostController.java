package com.CareerNexus_Backend.CareerNexus.controller;

import com.CareerNexus_Backend.CareerNexus.dto.JobPostDTO;
import com.CareerNexus_Backend.CareerNexus.dto.RecruiterJobsDTO;
import com.CareerNexus_Backend.CareerNexus.service.JobPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobPostController {
    @Autowired private JobPostService jobPostService;

    @PostMapping("/post")
    public ResponseEntity<JobPostDTO> postJob(@RequestBody JobPostDTO dto, Authentication authentication) {
        dto.setPostedAt(LocalDate.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(jobPostService.createJobPost(dto, authentication.getName()));
    }
    @GetMapping("/recruiter")
    public ResponseEntity<List<JobPostDTO>> getJobsByRecruiter(Authentication authentication) {
        return ResponseEntity.ok(jobPostService.getAllJobsByRecruiter(authentication.getName()));
    }
    @GetMapping("/all")
    public ResponseEntity<List<JobPostDTO>> getAllJobs(Authentication authentication) {
        return ResponseEntity.ok(jobPostService.getAllJobs(authentication.getName()));
    }
    @GetMapping("/latest")
    public ResponseEntity<List<JobPostDTO>> getLatestJobs(Authentication authentication) {
        return ResponseEntity.ok(jobPostService.getLatestJobs(authentication.getName()));
    }
    @PutMapping("/{jobId}")
    public ResponseEntity<JobPostDTO> updateJob(@PathVariable Long jobId, @RequestBody JobPostDTO dto, Authentication authentication) throws Exception {
        return ResponseEntity.ok(jobPostService.updateJobPost(jobId, dto, authentication.getName()));
    }
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long jobId, Authentication authentication) throws Exception {
        jobPostService.deleteJobPost(jobId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/recruiters/job")
    public List<RecruiterJobsDTO> getAll() { return jobPostService.getAll(); }
}
