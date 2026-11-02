package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.*;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.model.*;
import com.CareerNexus_Backend.CareerNexus.repository.*;
import com.CareerNexus_Backend.CareerNexus.security.EncryptionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentService.class);

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private JobPostRepository jobPostRepository;

    @Autowired
    private uploadHandler uploadFileHandler;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private QuestionExtractionService questionExtractionService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private  AssessmentNotificationService assessmentNotificationService;

    @Autowired
    private StudentExamAttemptRepository studentExamAttempt;

    @Autowired
    private CodingAssessmentResultRepository codingAssessmentResultRepository;

    @Autowired
    private CodingAssessmentRepository codingAssessmentRepository;

    @org.springframework.beans.factory.annotation.Value("${file.upload-assessment-files}")
    private String uploadAssessmentDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ResponseEntity<String> assessmentConfiguration(AssessmentRoundDto assessmentRoundDto, MultipartFile questionPdf) throws Exception {
        User user = userAuthRepository.findById(assessmentRoundDto.getCreatedByUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + assessmentRoundDto.getCreatedByUserId()));
        JobPost jobPost = jobPostRepository.findById(assessmentRoundDto.getJobPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Job post not found with Id: " + assessmentRoundDto.getJobPostId()));

        if (!jobPost.getPostedBy().getUserId().equals(user.getUserId()))
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException("You can only configure assessments for your own jobs.");
        if (assessmentRoundDto.getStartTime() == null || assessmentRoundDto.getEndTime() == null || !assessmentRoundDto.getEndTime().isAfter(assessmentRoundDto.getStartTime()))
            throw new IllegalArgumentException("The end time must be after the start time.");
        // Upload files and get their stored paths.
        String questionPdfPath = uploadFileHandler.uploadFileHandler(questionPdf, assessmentRoundDto.getCreatedByUserId(), assessmentRoundDto.getJobPostId());

        if (questionPdfPath == null) {
            throw new RuntimeException("Failed to upload PDF file.");
        }

        Path pdfFilePath = Paths.get(uploadAssessmentDir).resolve(questionPdfPath.startsWith("/") ? questionPdfPath.substring(1) : questionPdfPath);
        File pdfFile = pdfFilePath.toFile();

        String response = null;
        try {
            FileSystemResource fileResource = new FileSystemResource(pdfFile);
            logger.info("Requesting Gemini model to extract data from pdf for job ID: {}", assessmentRoundDto.getJobPostId());
            response = questionExtractionService.extractQuestionsFromFile(fileResource);
            logger.debug("Gemini response: {}", response);
        } finally {
            // Ensure temporary file is always deleted
            Files.deleteIfExists(pdfFilePath);
        }
        ObjectMapper objectMapper = new ObjectMapper();

        // 2. Parse the JSON string into a generic Map instead of DTOs eliminate unnecessary response by using substring concept
        Map<String, Object> extractedData =
                objectMapper.readValue(response, new TypeReference<>() {});

        // 3. Extract the questions and answers lists from the Map
        Object questionsData = extractedData.get("questions");
        Object answersData = extractedData.get("answers");

        // 4. Convert the separated lists back into their own JSON strings
        String questionsOnlyJson = objectMapper.writeValueAsString(questionsData);
        String answersOnlyJson = objectMapper.writeValueAsString(answersData);


        String encryptionKey = encryptionService.generateKey();

        AssessmentRound assessmentRound = new AssessmentRound(
                user,
                jobPost,
                assessmentRoundDto.getRoundName(),
                assessmentRoundDto.getMin_marks(),
                encryptionService.encrypt(questionsOnlyJson, encryptionKey),
                encryptionService.encrypt(answersOnlyJson, encryptionKey),
                encryptionKey,
                assessmentRoundDto.getStartTime(),
                assessmentRoundDto.getEndTime(),
                LocalDateTime.now()
        );

        // Persist the assessment round first so that it has a valid ID for email links.
        AssessmentRound savedAssessmentRound = assessmentRepository.save(assessmentRound);

        assessmentNotificationService.notifyRegisteredStudents(
                jobPost.getId(),
                jobPost.getJobTitle(),
                assessmentRoundDto.getRoundName(),
                assessmentRoundDto.getStartTime().toString(),
                assessmentRoundDto.getEndTime().toString(),
                savedAssessmentRound.getId()
        );

        return ResponseEntity.ok("Round Configured Successfully...");

    }

    public List<AssessmentRoundDto> getAssessmentConfigurationData(String recruiterId, Long jobId) {
        logger.info("Fetching assessment configuration for recruiter: {} and job ID: {}", recruiterId, jobId);
        JobPost job = jobPostRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        User reviewer = userAuthRepository.findById(recruiterId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!"tpo".equalsIgnoreCase(reviewer.getRole()) && !job.getPostedBy().getUserId().equals(recruiterId))
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException("You can only review your own recruitment rounds.");
        List<AssessmentRoundDto> rounds = new java.util.ArrayList<>(assessmentRepository.findAssessmentRoundsByJobId(jobId));
        rounds.forEach(round -> round.setType("MCQ"));
        codingAssessmentRepository.findByJobPost_Id(jobId).forEach(ar -> {
            AssessmentRoundDto dto = new AssessmentRoundDto(ar.getCreatedBy().getUserId(), jobId, ar.getAssessmentName(), ar.getStartTime(), ar.getEndTime(), ar.getMinMarks() == null ? 0 : ar.getMinMarks(), ar.getId());
            dto.setType("CODING"); rounds.add(dto);
        });
        return rounds;
    }

    public List<AssessmentRoundDto> getAssessmentConfigurationForJobId(Long jobId, String studentId) {
        // 1. Get MCQs
        List<AssessmentRoundDto> mcqRounds = assessmentRepository.findConfigurationByJobId(jobId);
        mcqRounds.forEach(round -> {
            round.setMin_marks(0);
            round.setType("MCQ");
            round.setExamUrl("/student/" + studentId + "/test/" + round.getExamId());
            // Check if student already attempted this MCQ round
            User user = userAuthRepository.findById(studentId).orElse(null);
            AssessmentRound ar = assessmentRepository.findById(round.getExamId()).orElse(null);
            if (user != null && ar != null) {
                round.setAttempted(studentExamAttempt.findByStudentAndAssessmentRound(user, ar).isPresent());
            }
        });

        // 2. Get Coding Assessments
        List<CodingAssessment> codingRounds = codingAssessmentRepository.findByJobPost_Id(jobId);
        List<AssessmentRoundDto> convertedCodingRounds = codingRounds.stream().map(ar -> {
            AssessmentRoundDto dto = new AssessmentRoundDto();
            dto.setRoundName(ar.getAssessmentName());
            dto.setStartTime(ar.getStartTime());
            dto.setEndTime(ar.getEndTime());
            dto.setExamId(ar.getId());
            dto.setMin_marks(ar.getMinMarks() != null ? ar.getMinMarks() : 0);
            dto.setType("CODING");
            dto.setExamUrl("/student/" + studentId + "/coding-assessment/" + ar.getId());
            // Check if student already attempted this Coding round
            dto.setAttempted(codingAssessmentResultRepository.findByStudent_UserIdAndAssessment_Id(studentId, ar.getId()).isPresent());
            return dto;
        }).collect(Collectors.toList());

        // 3. Merge and Sort by startTime
        mcqRounds.addAll(convertedCodingRounds);
        mcqRounds.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        return mcqRounds;
    }

    public StudentExamDTO getQuestionsForStudent(Long assessmentId, String studentId) throws Exception {
        // 1. Fetch from DB
        AssessmentRound assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment Round not found with ID: " + assessmentId));

        if (applicationRepository.findByJobPost_IdAndStudent_UserId(assessment.getJobPost().getId(), studentId).isEmpty())
            throw new IllegalArgumentException("Apply for this role before opening its assessment.");
        // 2. Check for existing attempt
        User user = userAuthRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + studentId));
        if (studentExamAttempt.findByStudentAndAssessmentRound(user, assessment).isPresent()) {
            throw new RuntimeException("Access Denied: You have already submitted this assessment.");
        }

        // 3. Security Check: Is the exam active?
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(assessment.getStartTime())) {
            logger.warn("User attempted to start exam too early. Assessment ID: {}", assessmentId);
            throw new RuntimeException("Exam has not started yet.");
        }
        if (now.isAfter(assessment.getEndTime())) {
            logger.warn("User attempted to start exam after deadline. Assessment ID: {}", assessmentId);
            throw new RuntimeException("Exam window has closed.");
        }


        // 3. Decrypt the questions from the secure storage
        String decryptedJson = encryptionService.decrypt(
                assessment.getEncryptedQuestions(),
                assessment.getEncryptionKey()
        );

        // 4. Use TypeReference to parse the JSON Array [...]
        List<QuestionDto> rawQuestions = objectMapper.readValue(
                decryptedJson,
                new TypeReference<List<QuestionDto>>() {}
        );

        // 5. THE CRITICAL STEP: Extract only Question data fields (Answer Stripping)
        List<QuestionDto> questionsOnly = rawQuestions.stream()
                .map(q -> {
                    QuestionDto stripped = new QuestionDto();
                    stripped.setQuestionNo(q.getQuestionNo());
                    stripped.setQuestionText(q.getQuestionText());
                    stripped.setOptions(q.getOptions());
                    // Ensure correct_answer is never set
                    return stripped;
                })
                .collect(Collectors.toList());

        // Log for verification
        logger.info("Questions dispatched for assessment {}: {} items.", assessmentId, questionsOnly.size());

        // 6. FIX: Wrap the list into a StudentExamDto object
        StudentExamDTO response = new StudentExamDTO();

        response.setAssessmentId(assessment.getId());
        response.setRoundName(assessment.getRoundName());
        response.setQuestions(questionsOnly);
        response.setEndTime(assessment.getEndTime());

        return response;
    }


    @Transactional
    public StudentExamAttempt processSubmission(Long assessmentId, String studentUserId, List<StudentAnswerSubmissionDto> studentAnswers) throws Exception {
        User user = userAuthRepository.findById(studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + studentUserId));

        AssessmentRound assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        if (applicationRepository.findByJobPost_IdAndStudent_UserId(assessment.getJobPost().getId(), studentUserId).isEmpty())
            throw new IllegalArgumentException("Apply for this role before submitting its assessment.");
        // 1. Decrypt Ground Truth (Answers) from its separate column
        String decryptedAnswers = encryptionService.decrypt(assessment.getEncryptedAnswers(), assessment.getEncryptionKey());
        List<AnswerDto> correctAnswers = objectMapper.readValue(decryptedAnswers, new TypeReference<List<AnswerDto>>() {});

        // Map for fast lookup: QuestionNo -> Correct String
        Map<String, String> truthMap = correctAnswers.stream()
                .collect(Collectors.toMap(AnswerDto::getQuestionNo, AnswerDto::getCorrectAnswer));

        // 2. BLOCKING LOGIC: Check if an attempt already exists
        if(studentExamAttempt.findByStudentAndAssessmentRound(user, assessment).isPresent()) {
            throw new RuntimeException("Access Denied: You have already submitted this assessment.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(assessment.getStartTime())) {
            throw new RuntimeException("The exam window opens at: " + assessment.getStartTime());
        }
        if (now.isAfter(assessment.getEndTime())) {
            throw new RuntimeException("The exam window has already closed.");
        }

        // 2. Evaluation Logic
        int score = 0;
        logger.info("Evaluating submission for assessment {}. Submitted answers count: {}", assessmentId, studentAnswers.size());

        java.util.Set<Integer> answeredQuestions = new java.util.HashSet<>();
        for (StudentAnswerSubmissionDto sub : studentAnswers) {
            if (sub != null && !answeredQuestions.add(sub.getQueNo())) throw new IllegalArgumentException("Answer each question only once.");
            if (sub == null || sub.getQueNo() <= 0 || sub.getOption() == null) {
                logger.warn("Skipping invalid answer submission: {}", sub);
                continue;
            }
            String correct = truthMap.get(String.valueOf(sub.getQueNo()));
            if (correct != null && correct.trim().equalsIgnoreCase(sub.getOption().trim())) {
                score++;
            }
        }

        // 3. Status Determination based on min_marks defined in AssessmentRound
        boolean isPassed = score >= assessment.getMin_marks();
        String resultStatus = isPassed ? "PASSED" : "FAILED";

        // 4. Save Attempt Record (using your Model from the Canvas)
        StudentExamAttempt attempt = new StudentExamAttempt();
        attempt.setStudent(user);
        attempt.setAssessmentRound(assessment);
        attempt.setScore(score);
        attempt.setTotalQuestions(correctAnswers.size());
        attempt.setResultStatus(resultStatus);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setStudentAnswersJson(objectMapper.writeValueAsString(studentAnswers));

        StudentExamAttempt savedAttempt = studentExamAttempt.save(attempt);

        // 5. Integration with main Application Status
        // If they fail the assessment, we reject their overall job application.
        if (!isPassed) {
            Application application =applicationRepository .findByJobPost_IdAndStudent_UserId(
                    assessment.getJobPost().getId(),
                    studentUserId
            ).orElse(null);

            if (application != null) {
                application.setStatus("REJECTED");
                application.setFeedback("Did not meet minimum marks in " + assessment.getRoundName());
                applicationRepository.save(application);
            }
        }

        return savedAttempt;
    }

    /**
     * Helper for building DTOs for recruiter round views.
     */
    private AssessmentRoundStudentStatusDto toStatusDto(AssessmentRound round, StudentExamAttempt attempt, Application application) {
        return new AssessmentRoundStudentStatusDto(
                round.getId(),
                round.getJobPost() != null ? round.getJobPost().getId() : null,
                attempt.getStudent().getUserId(),
                attempt.getStudent().getEmail(),
                application != null ? application.getId() : null,
                application != null ? application.getStatus() : null,
                application != null ? application.getApplicationDate() : null,
                attempt.getScore(),
                attempt.getTotalQuestions(),
                attempt.getResultStatus()
        );
    }

    public List<AssessmentRoundStudentStatusDto> getPassedStudentsForRound(Long assessmentRoundId) {
        AssessmentRound round = assessmentRepository.findById(assessmentRoundId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment Round not found with ID: " + assessmentRoundId));

        List<StudentExamAttempt> attempts = studentExamAttempt.findByAssessmentRound_IdAndResultStatus(assessmentRoundId, "PASSED");

        return attempts.stream()
                .map(attempt -> {
                    Application app = applicationRepository
                            .findByJobPost_IdAndStudent_UserId(
                                    round.getJobPost().getId(),
                                    attempt.getStudent().getUserId()
                            ).orElse(null);
                    return toStatusDto(round, attempt, app);
                })
                .collect(Collectors.toList());
    }

    public List<AssessmentRoundStudentStatusDto> getFailedStudentsForRound(Long assessmentRoundId) {
        AssessmentRound round = assessmentRepository.findById(assessmentRoundId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment Round not found with ID: " + assessmentRoundId));

        List<StudentExamAttempt> attempts = studentExamAttempt.findByAssessmentRound_IdAndResultStatus(assessmentRoundId, "FAILED");

        return attempts.stream()
                .map(attempt -> {
                    Application app = applicationRepository
                            .findByJobPost_IdAndStudent_UserId(
                                    round.getJobPost().getId(),
                                    attempt.getStudent().getUserId()
                            ).orElse(null);
                    return toStatusDto(round, attempt, app);
                })
                .collect(Collectors.toList());
    }

    /**
     * Pending = applied for the job but has not submitted this assessment round yet.
     */
    public List<AssessmentRoundStudentStatusDto> getPendingStudentsForRound(Long assessmentRoundId) {
        AssessmentRound round = assessmentRepository.findById(assessmentRoundId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment Round not found with ID: " + assessmentRoundId));

        Long jobId = round.getJobPost().getId();

        // All applications for this job
        List<Application> applications = applicationRepository.findByJobPost_Id(jobId);

        // All attempts for this round
        List<StudentExamAttempt> attempts = studentExamAttempt.findByAssessmentRound_Id(assessmentRoundId);
        Set<String> attemptedStudentIds = attempts.stream()
                .map(a -> a.getStudent().getUserId())
                .collect(Collectors.toSet());

        // Filter applications where student has not attempted this round
        return applications.stream()
                .filter(app -> !attemptedStudentIds.contains(app.getStudent().getUserId()))
                .map(app -> new AssessmentRoundStudentStatusDto(
                        round.getId(),
                        jobId,
                        app.getStudent().getUserId(),
                        app.getStudent().getEmail(),
                        app.getId(),
                        app.getStatus(),
                        app.getApplicationDate(),
                        null,
                        null,
                        "PENDING"
                ))
                .collect(Collectors.toList());
    }
}