package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.*;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.model.*;
import com.CareerNexus_Backend.CareerNexus.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CodingAssessmentService {

    @Autowired
    private CodingAssessmentRepository assessmentRepository;
    
    @Autowired
    private CodingQuestionRepository questionRepository;
    
    @Autowired
    private TestCaseRepository testCaseRepository;
    
    @Autowired
    private UserAuthRepository userRepository;
    
    @Autowired
    private JobPostRepository jobPostRepository;

    @Autowired
    private CodingAssessmentResultRepository resultRepository;

    @Autowired
    private CodeExecutionService executionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Transactional
    public CodingAssessment createCodingAssessment(CodingAssessmentRequestDto requestDto, String tpoId) {
        
        User tpo = userRepository.findById(tpoId)
                .orElseThrow(() -> new ResourceNotFoundException("TPO User not found."));

        if (requestDto.getAssessmentName() == null || requestDto.getAssessmentName().isBlank()
                || requestDto.getStartTime() == null || requestDto.getEndTime() == null
                || !requestDto.getEndTime().isAfter(requestDto.getStartTime()))
            throw new IllegalArgumentException("Add an assessment name and a valid start/end time.");
        if (requestDto.getQuestions() == null || requestDto.getQuestions().isEmpty())
            throw new IllegalArgumentException("Add at least one question.");
        for (CodingQuestionDto question : requestDto.getQuestions()) {
            if (question.getTitle() == null || question.getTitle().isBlank() || question.getPoints() <= 0
                    || question.getTestCases() == null || question.getTestCases().isEmpty())
                throw new IllegalArgumentException("Each question needs a title, positive points, and a test case.");
        }
        JobPost jobPost = null;
        if (requestDto.getJobPostId() != null) {
            jobPost = jobPostRepository.findById(requestDto.getJobPostId())
                    .orElseThrow(() -> new ResourceNotFoundException("JobPost not found."));
        }

        if ("recruiter".equalsIgnoreCase(tpo.getRole()) && (jobPost == null
                || !jobPost.getPostedBy().getUserId().equals(tpoId)))
            throw new IllegalArgumentException("Recruiters can only configure rounds for their own jobs.");
        // 1. Create top-level Assessment
        CodingAssessment assessment = new CodingAssessment(
                requestDto.getAssessmentName(),
                tpo,
                jobPost,
                requestDto.getStartTime(),
                requestDto.getEndTime()
        );
        // Set mode (PRACTICE by default) and minimum marks if provided
        String mode = requestDto.getMode() != null ? requestDto.getMode() : "PRACTICE";
        assessment.setAssessmentMode(mode);
        assessment.setMinMarks(requestDto.getMinMarks());

        CodingAssessment savedAssessment = assessmentRepository.save(assessment);

        // 2. Loop and map Questions
        if (requestDto.getQuestions() != null) {
            for (CodingQuestionDto qDto : requestDto.getQuestions()) {
                CodingQuestion question = new CodingQuestion(
                        savedAssessment,
                        qDto.getTitle(),
                        qDto.getDescription(),
                        qDto.getConstraints(),
                        qDto.getPoints()
                );
                CodingQuestion savedQuestion = questionRepository.save(question);

                // 3. Loop and map Test Cases
                if (qDto.getTestCases() != null) {
                    for (TestCaseDto tcDto : qDto.getTestCases()) {
                        TestCase testCase = new TestCase(
                                savedQuestion,
                                tcDto.getInput(),
                                tcDto.getExpectedOutput(),
                                tcDto.getIsHidden(),
                                tcDto.getMarks()
                        );
                        testCaseRepository.save(testCase);
                    }
                }
            }
        }

        return savedAssessment;
    }

    @Transactional(readOnly = true)
    public StudentCodingAssessmentDto getStudentCodingAssessment(Long assessmentId, String studentId) {
        CodingAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Coding Assessment not found."));

        requireAssessmentAccess(assessment, studentId);
        List<CodingQuestion> questions = questionRepository.findByCodingAssessmentId(assessmentId);
        
        List<StudentCodingQuestionDto> questionDtos = questions.stream().map(q -> {
            List<TestCase> publicTestCases = testCaseRepository.findByCodingQuestionId(q.getId())
                    .stream()
                    .filter(tc -> !tc.isHidden())
                    .collect(Collectors.toList());

            List<StudentTestCaseDto> publicTestCaseDtos = publicTestCases.stream()
                    .map(tc -> new StudentTestCaseDto(tc.getId(), tc.getInput(), tc.getExpectedOutput(), tc.getMarks()))
                    .collect(Collectors.toList());

            return new StudentCodingQuestionDto(
                    q.getId(),
                    q.getTitle(),
                    q.getDescription(),
                    q.getConstraints(),
                    q.getPoints(),
                    publicTestCaseDtos
            );
        }).collect(Collectors.toList());

        // Check if student already has a result for this assessment (practice history)
        CodingAssessmentResult result = resultRepository
                .findByStudent_UserIdAndAssessment_Id(studentId, assessmentId)
                .orElse(null);

        if (result != null) {
            throw new RuntimeException("Access Denied: You have already submitted this assessment.");
        }

        Boolean solved = false;
        Integer totalScore = null;
        Integer maxScore = null;

        return new StudentCodingAssessmentDto(
                assessment.getId(),
                assessment.getAssessmentName(),
                assessment.getJobPost() != null ? assessment.getJobPost().getId() : null,
                assessment.getStartTime().toString(),
                assessment.getEndTime().toString(),
                questionDtos,
                solved,
                totalScore,
                maxScore
        );
    }

    @Transactional
    public CodingAssessmentResult submitCodingAssessment(Long assessmentId, CodeSubmissionRequestDto submissionDto, String studentId) {
        CodingAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Coding Assessment not found."));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));

        requireAssessmentAccess(assessment, studentId);
        if (resultRepository.findByStudent_UserIdAndAssessment_Id(studentId, assessmentId).isPresent())
            throw new IllegalArgumentException("You have already submitted this assessment.");
        if (submissionDto.getSubmissions() == null || submissionDto.getSubmissions().isEmpty())
            throw new IllegalArgumentException("Add code for at least one question before submitting.");
        java.util.Set<Long> submittedIds = new java.util.HashSet<>();
        int totalScore = 0;
        int maxScore = questionRepository.findByCodingAssessmentId(assessmentId).stream().mapToInt(CodingQuestion::getPoints).sum();

        for (CodeSubmissionRequestDto.QuestionSubmission sub : submissionDto.getSubmissions()) {
            CodingQuestion question = questionRepository.findById(sub.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found."));
            
            if (!question.getCodingAssessment().getId().equals(assessmentId) || !submittedIds.add(question.getId()))
                throw new IllegalArgumentException("Submit each question from this assessment only once.");

            List<TestCase> allTestCases = testCaseRepository.findByCodingQuestionId(question.getId());
            
            for (TestCase tc : allTestCases) {
                try {
                    CodeExecutionResponseDto response = executionService.executeCode(sub.getLanguage(), sub.getCode(), tc.getInput());
                    
                    String actualOutput = response.getStdout() != null ? response.getStdout().trim() : "";
                    String expectedOutput = tc.getExpectedOutput() != null ? tc.getExpectedOutput().trim() : "";

                    if (actualOutput.equals(expectedOutput)) {
                        totalScore += tc.getMarks();
                    }
                } catch (Exception e) {
                    throw new com.CareerNexus_Backend.CareerNexus.exceptions.ExternalServiceUnavailableException("Code execution is unavailable. Your assessment has not been submitted; please try again.", e);
                }
            }
        }

        String submissionDataJson = "";
        try {
            submissionDataJson = objectMapper.writeValueAsString(submissionDto);
        } catch (Exception e) {
            submissionDataJson = "Error serializing submission data";
        }

        // --- LATEST CODE ONLY LOGIC ---
        // Check if a result already exists for this student and assessment
        CodingAssessmentResult result = resultRepository.findByStudent_UserIdAndAssessment_Id(studentId, assessmentId)
                .orElse(new CodingAssessmentResult(assessment, student, 0, 0, ""));

        result.setTotalScore(totalScore);
        result.setMaxScore(maxScore);
        result.setSubmissionData(submissionDataJson);
        result.setSubmittedAt(LocalDateTime.now());

        CodingAssessmentResult savedResult = resultRepository.save(result);

        // If this coding assessment is configured as an elimination round,
        // reject the student's job application when they don't meet minimum marks.
        if ("ELIMINATION".equalsIgnoreCase(assessment.getAssessmentMode())
                && assessment.getJobPost() != null
                && assessment.getMinMarks() != null
                && savedResult.getTotalScore() < assessment.getMinMarks()) {

            Application application = applicationRepository
                    .findByJobPost_IdAndStudent_UserId(
                            assessment.getJobPost().getId(),
                            studentId
                    ).orElse(null);

            if (application != null) {
                application.setStatus("REJECTED");
                application.setFeedback("Did not meet minimum marks in Coding Round");
                applicationRepository.save(application);
            }
        }

        return savedResult;
    }

    @Transactional(readOnly = true)
    public List<StudentAssessmentDashboardDto> getStudentAssessmentDashboard(String studentId) {
        List<CodingAssessment> assessments = assessmentRepository.findAllByOrderByCreatedAtDesc();
        List<StudentAssessmentDashboardDto> dashboard = new ArrayList<>();

        for (CodingAssessment assessment : assessments) {
            CodingAssessmentResult result = resultRepository.findByStudent_UserIdAndAssessment_Id(studentId, assessment.getId())
                    .orElse(null);

            dashboard.add(new StudentAssessmentDashboardDto(
                    assessment.getId(),
                    assessment.getAssessmentName(),
                    assessment.getJobPost() != null ? assessment.getJobPost().getId() : null,
                    assessment.getStartTime(),
                    assessment.getEndTime(),
                    result != null,
                    result != null ? result.getTotalScore() : null,
                    result != null ? result.getMaxScore() : null
            ));
        }

        return dashboard;
    }
    private void requireAssessmentAccess(CodingAssessment assessment, String studentId) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(assessment.getStartTime())) throw new IllegalArgumentException("This assessment has not started yet.");
        if (now.isAfter(assessment.getEndTime())) throw new IllegalArgumentException("The assessment window has closed.");
        if (assessment.getJobPost() != null && applicationRepository.findByJobPost_IdAndStudent_UserId(assessment.getJobPost().getId(), studentId).isEmpty())
            throw new IllegalArgumentException("Apply for this role before opening its assessment.");
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> getRoundResults(Long assessmentId, String reviewerId) {
        CodingAssessment assessment = assessmentRepository.findById(assessmentId).orElseThrow(() -> new ResourceNotFoundException("Coding assessment not found."));
        User reviewer = userRepository.findById(reviewerId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!"tpo".equalsIgnoreCase(reviewer.getRole()) && !assessment.getCreatedBy().getUserId().equals(reviewerId))
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException("You can only review your own coding rounds.");
        java.util.Map<String, java.util.List<java.util.Map<String, Object>>> groups = new java.util.LinkedHashMap<>();
        groups.put("passed", new java.util.ArrayList<>()); groups.put("failed", new java.util.ArrayList<>()); groups.put("pending", new java.util.ArrayList<>());
        if (assessment.getJobPost() == null) return groups;
        for (Application app : applicationRepository.findByJobPost_Id(assessment.getJobPost().getId())) {
            CodingAssessmentResult result = resultRepository.findByStudent_UserIdAndAssessment_Id(app.getStudent().getUserId(), assessmentId).orElse(null);
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("studentUserId", app.getStudent().getUserId()); row.put("studentEmail", app.getStudent().getEmail()); row.put("applicationStatus", app.getStatus());
            row.put("score", result == null ? null : result.getTotalScore()); row.put("totalQuestions", result == null ? null : result.getMaxScore());
            String group = result == null ? "pending" : result.getTotalScore() >= (assessment.getMinMarks() == null ? 0 : assessment.getMinMarks()) ? "passed" : "failed";
            groups.get(group).add(row);
        }
        return groups;
    }

}
