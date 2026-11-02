package com.CareerNexus_Backend.CareerNexus.controller;


import com.CareerNexus_Backend.CareerNexus.dto.ApplicationDTO;
import com.CareerNexus_Backend.CareerNexus.dto.JobApplicationCountDTO;
import com.CareerNexus_Backend.CareerNexus.dto.StudentsApplicationsDTO;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    @Autowired
    private ApplicationService applicationService;

    @PostMapping(path = "/apply/{jobId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationDTO> applyForJob(@PathVariable Long jobId, @RequestPart("resumeFile") MultipartFile resumeFile, Authentication authentication) throws Exception {
        String userId = authentication.getName();
        ApplicationDTO createdApplication = applicationService.applyForJob(jobId, userId,resumeFile);
        return new ResponseEntity<>(createdApplication, HttpStatus.CREATED);
    }




    @GetMapping("/my-applications")
    public ResponseEntity<List<ApplicationDTO>> getMyApplications(Authentication authentication) {
        try {
            String userId = authentication.getName();
            List<ApplicationDTO> applications = applicationService.getApplicationsByStudent(userId);
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<ApplicationDTO>> getApplicationsForJob(@PathVariable Long jobId, Authentication authentication) {
        applicationService.requireReviewAccess(jobId, authentication.getName());
        try {
            List<ApplicationDTO> applications = applicationService.getApplicationsForJob(jobId);
            return ResponseEntity.ok(applications);
        }  catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @GetMapping("/counts/per-job/by-recruiter/{recruiterId}")
    public ResponseEntity<List<JobApplicationCountDTO>> getApplicationCountsPerJobForRecruiter(@PathVariable String recruiterId, Authentication authentication) {
        applicationService.requireCountAccess(recruiterId, authentication.getName());
        try {
            List<JobApplicationCountDTO> counts = applicationService.getApplicationCountsPerJobForRecruiter(recruiterId);
            return ResponseEntity.ok(counts);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
  @GetMapping("/student/applications/{id}")
    public List<StudentsApplicationsDTO> getStudentApplicationsForJobId(@PathVariable Long id, Authentication authentication){
        applicationService.requireReviewAccess(id, authentication.getName());
        return applicationService.getStudentApplicationsForJobId(id);
    }

    @GetMapping("student/apply/count")
    public  JobApplicationCountDTO studentApplicationCount(Authentication authentication){
        String userId = authentication.getName();
        return applicationService.getCountApplicationByStudent(userId);
    }
}