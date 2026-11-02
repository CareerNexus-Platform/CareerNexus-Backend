package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.ApplicationDTO;
import com.CareerNexus_Backend.CareerNexus.dto.JobApplicationCountDTO;
import com.CareerNexus_Backend.CareerNexus.dto.StudentsApplicationsDTO;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.model.Application;
import com.CareerNexus_Backend.CareerNexus.dto.AssessmentRoundDto;
import com.CareerNexus_Backend.CareerNexus.model.JobPost;
import com.CareerNexus_Backend.CareerNexus.model.User;
import com.CareerNexus_Backend.CareerNexus.repository.ApplicationRepository;
import com.CareerNexus_Backend.CareerNexus.repository.AssessmentRepository;
import com.CareerNexus_Backend.CareerNexus.repository.JobPostRepository;
import com.CareerNexus_Backend.CareerNexus.repository.UserAuthRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JobPostRepository jobPostRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private AssessmentNotificationService assessmentNotificationService;

    @Autowired
    private EmailService emailService;

    @Transactional
    public ApplicationDTO applyForJob(Long jobId, String studentUserId, MultipartFile resumeFile) throws Exception {

        JobPost jobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost not found with ID: " + jobId));


        User student = userAuthRepository.findById(studentUserId)
                .orElseThrow(() -> new Exception("Student User not found with ID: " + studentUserId));


        if (!"student".equalsIgnoreCase(student.getRole())) {
            logger.warn("Non-student user {} (Role: {}) attempted to apply for job {}", 
                    studentUserId, student.getRole(), jobId);
            throw new IllegalArgumentException("Only users with 'STUDENT' role can apply for jobs.");
        }


        if (jobPost.getApplicationDeadline() != null && jobPost.getApplicationDeadline().isBefore(java.time.LocalDate.now()))
            throw new IllegalArgumentException("Applications for this role are closed.");
        if (resumeFile != null && !resumeFile.isEmpty() && !"application/pdf".equalsIgnoreCase(resumeFile.getContentType()))
            throw new IllegalArgumentException("Upload your resume as a PDF.");

        Optional<Application> existingApplication = applicationRepository.findByJobPost_IdAndStudent_UserId(jobId, studentUserId);
        if (existingApplication.isPresent()) {
            throw new IllegalArgumentException("Student has already applied for this job.");
        }


        String resumeUrl = null;
        if (resumeFile != null && !resumeFile.isEmpty()) {
            try {
                logger.info("Uploading resume for student {} and job {}", studentUserId, jobId);
                resumeUrl = supabaseStorageService.uploadResume(resumeFile, jobId.toString(), studentUserId);
                logger.info("Resume uploaded successfully to Supabase for student {}", studentUserId);
            } catch (IOException e) {
                logger.error("Failed to upload resume for student {}: {}", studentUserId, e.getMessage());
                throw new RuntimeException("Failed to store resume file in Supabase: " + e.getMessage(), e);
            }
        } else {
            logger.warn("Resume file is missing for student application: {}", studentUserId);
            throw new IllegalArgumentException("Resume file is required for application.");
        }
        Application application = new Application(jobPost, student, resumeUrl);
        Application savedApplication = applicationRepository.save(application);

        // --- APPLICATION CONFIRMATION EMAIL ---
        try {
            emailService.sendApplicationConfirmationEmail(
                    student.getEmail(),
                    student.getUserId(),
                    jobPost.getCompanyName(),
                    jobPost.getJobTitle(),
                    jobPost.getLocations()
            );
        } catch (Exception e) {
            logger.error("Failed to send application confirmation email to {}. Error: {}", student.getEmail(), e.getMessage());
            // Non-blocking error
        }

        // --- LATE APPLICANT EXAM NOTIFICATION ---
        // Check if there are any assessment rounds already configured for this job post.
        List<AssessmentRoundDto> rounds = assessmentRepository.findConfigurationByJobId(jobId);
        
        // If there are rounds, immediately dispatch an exam invite to the new student.
        if (rounds != null && !rounds.isEmpty()) {
            for (AssessmentRoundDto round : rounds) {
                try {
                    assessmentNotificationService.notifySingleStudent(
                            student,
                            jobPost.getJobTitle(),
                            round.getRoundName(),
                            round.getStartTime() != null ? round.getStartTime().toString() : "",
                            round.getEndTime() != null ? round.getEndTime().toString() : "",
                            round.getExamId()
                    );
                } catch (Exception e) {
                    logger.error("Failed to send exam notification to late applicant {}", studentUserId, e);
                    // We don't want to fail the actual job application just because the email failed.
                }
            }
        }

        return new ApplicationDTO(savedApplication);
    }

    @Transactional()
    public ApplicationDTO getApplicationById(Long applicationId) throws Exception {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new Exception("Application not found with ID: " + applicationId));
        return new ApplicationDTO(application);
    }

    @Transactional()
    public List<ApplicationDTO> getApplicationsByStudent(String studentUserId) {
        List<Application> applications = applicationRepository.findByStudent_UserId(studentUserId);
        return applications.stream()
                .map(ApplicationDTO::new)
                .collect(Collectors.toList());
    }


    @Transactional()
    public List<ApplicationDTO> getApplicationsForJob(Long jobId) {
        List<Application> applications = applicationRepository.findByJobPost_Id(jobId);
        return applications.stream()
                .map(ApplicationDTO::new)
                .collect(Collectors.toList());
    }


    // it shows the total student applied for each individual job posted by specific recruiter
    @Transactional()
    public List<JobApplicationCountDTO> getApplicationCountsPerJobForRecruiter(String recruiterId) {
        User recruiter = userAuthRepository.findById(recruiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter user not found with ID: " + recruiterId));
        if (!"recruiter".equalsIgnoreCase(recruiter.getRole()) && !"tpo".equalsIgnoreCase(recruiter.getRole())) {
            throw new IllegalArgumentException("User ID " + recruiterId + " does not belong to a recruiter or TPO.");
        }
        return applicationRepository.countApplicationsWithDetailsPerJobForRecruiter(recruiter);
    }
    public JobApplicationCountDTO getCountById(Long id){
        return applicationRepository.countApplicationByJobId(id);
    }

    public List<StudentsApplicationsDTO> getStudentApplicationsForJobId(Long id) {
        return applicationRepository.findAllStudentsApplications(id);
    }

    public JobApplicationCountDTO getCountApplicationByStudent(String userId){

        return  applicationRepository.findCount(userId);
    }

    public void requireReviewAccess(Long jobId, String reviewerId) {
        User reviewer = userAuthRepository.findById(reviewerId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        JobPost job = jobPostRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        if (!"tpo".equalsIgnoreCase(reviewer.getRole()) && !job.getPostedBy().getUserId().equals(reviewerId))
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException("You can only review applications for your own jobs.");
    }
    public void requireCountAccess(String recruiterId, String reviewerId) {
        User reviewer = userAuthRepository.findById(reviewerId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!"tpo".equalsIgnoreCase(reviewer.getRole()) && !reviewerId.equals(recruiterId))
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException("You can only review your own application counts.");
    }

}