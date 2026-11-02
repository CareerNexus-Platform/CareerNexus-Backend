package com.CareerNexus_Backend.CareerNexus.controller;

import com.CareerNexus_Backend.CareerNexus.dto.RecruiterDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.service.RecruiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/recruiter")
public class RecruiterController {

    private static final Logger logger = LoggerFactory.getLogger(RecruiterController.class);

    @Autowired
    private RecruiterService recruiterService;

    @PostMapping("/profile")
    public RecruiterDetailsDTO Profile( @RequestPart(value = "imageFile", required = false) MultipartFile img,@RequestPart("data") RecruiterDetailsDTO RecruiterDetails, Authentication authentication) throws Exception {
        String userId = authentication.getName();
        logger.info("Received request to create/update profile for recruiter: {}", userId);
        return recruiterService.createOrUpdateProfile(userId,RecruiterDetails,img);
    }

    @GetMapping("/profile")
    public RecruiterDetailsDTO Profile(Authentication authentication) throws Exception {
        String userId = authentication.getName();
        logger.info("Received request to fetch profile for recruiter: {}", userId);
        return recruiterService.getProfileData(userId);
    }
}
