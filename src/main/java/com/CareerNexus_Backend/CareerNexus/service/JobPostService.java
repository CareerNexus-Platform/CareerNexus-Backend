package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.*;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException;
import com.CareerNexus_Backend.CareerNexus.model.JobPost;
import com.CareerNexus_Backend.CareerNexus.model.Recruiter;
import com.CareerNexus_Backend.CareerNexus.model.User;
import com.CareerNexus_Backend.CareerNexus.repository.JobPostRepository;

import com.CareerNexus_Backend.CareerNexus.repository.UserAuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobPostService {

    @Autowired
    private JobPostRepository jobPostRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;




    @Transactional
    public JobPostDTO createJobPost(JobPostDTO jobPostDTO, String  userId) {

        User user = userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        if (!"recruiter".equalsIgnoreCase(user.getRole())) throw new UnauthorizedActionException("Only recruiters can post jobs.");
        JobPost jobPost = convertDtoToEntity(jobPostDTO);

        jobPost.setPostedBy(user);
        jobPost.setPostedAt(LocalDate.now());
        JobPost savedJobPost = jobPostRepository.save(jobPost);


        return convertEntityToDto(savedJobPost);
    }

    @Transactional(readOnly = true) // Read-only transaction
    public List<JobPostDTO> getAllJobsByRecruiter(String recruiterUserId) {
        User user = userAuthRepository.findById(recruiterUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " +recruiterUserId));


        List<JobPost> jobs = jobPostRepository.findByPostedBy(user);
        return jobs.stream()
                .map(this::convertEntityToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JobPostDTO> getAllJobs(String userId) {
        User user = userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        List<JobPost> jobs = jobPostRepository.findNotAppliedJobsByStudent(user);
        return jobs.stream()
                .map(this::convertEntityToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JobPostDTO> getLatestJobs(String userId) {
        User user = userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        List<JobPost> jobs = jobPostRepository.findTop5LatestNotAppliedJobs(user, PageRequest.of(0, 5));
        return jobs.stream()
                .map(this::convertEntityToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobPostDTO getJobPostById(Long jobId) throws Exception {
        JobPost jobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job Post not found with ID: " + jobId));
        return convertEntityToDto(jobPost);
    }

    @Transactional // Add transactional for write operations
    public JobPostDTO updateJobPost(Long jobId, JobPostDTO updatedJobPostDTO, String recruiterId) throws Exception {
        JobPost existingJobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job Post not found with ID: " + jobId));


        requireOwner(existingJobPost, recruiterId);
        existingJobPost.setCompanyName(updatedJobPostDTO.getCompanyName());
        existingJobPost.setJobTitle(updatedJobPostDTO.getJobTitle());
        existingJobPost.setSalaryPackage(updatedJobPostDTO.getSalaryPackage());
        existingJobPost.setApplicationDeadline(updatedJobPostDTO.getApplicationDeadline());
        existingJobPost.setLocations(updatedJobPostDTO.getLocations());
        existingJobPost.setJobDescription(updatedJobPostDTO.getJobDescription());
        existingJobPost.setRecruitmentProcess(updatedJobPostDTO.getRecruitmentProcess());
        JobPost savedJobPost = jobPostRepository.save(existingJobPost);
        return convertEntityToDto(savedJobPost);
    }

    @Transactional
    public void deleteJobPost(Long jobId, String recruiterId) throws Exception {
        JobPost jobPost = jobPostRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job Post not found with ID: " + jobId));
        requireOwner(jobPost, recruiterId);
        jobPostRepository.delete(jobPost);
    }

    private void requireOwner(JobPost job, String recruiterId) {
        if (job.getPostedBy() == null || !job.getPostedBy().getUserId().equals(recruiterId))
            throw new UnauthorizedActionException("You can only change your own job postings.");
    }

    private JobPost convertDtoToEntity(JobPostDTO dto) {

        JobPost jobPost = new JobPost();

        jobPost.setCompanyName(dto.getCompanyName());
        jobPost.setJobTitle(dto.getJobTitle());
        jobPost.setSalaryPackage(dto.getSalaryPackage());
        jobPost.setApplicationDeadline(dto.getApplicationDeadline());
        jobPost.setLocations(dto.getLocations());
        jobPost.setJobDescription(dto.getJobDescription());
        jobPost.setRecruitmentProcess(dto.getRecruitmentProcess());
        jobPost.setPostedAt(dto.getPostedAt());
        return jobPost;
    }

    private JobPostDTO convertEntityToDto(JobPost entity) {
        JobPostDTO dto = new JobPostDTO();
        dto.setId(entity.getId());
        dto.setCompanyName(entity.getCompanyName());
        dto.setJobTitle(entity.getJobTitle());
        dto.setSalaryPackage(entity.getSalaryPackage());
        dto.setApplicationDeadline(entity.getApplicationDeadline());
        dto.setLocations(entity.getLocations());
        dto.setJobDescription(entity.getJobDescription());
        dto.setRecruitmentProcess(entity.getRecruitmentProcess());
        dto.setPostedAt(entity.getPostedAt());
        return dto;
    }

    public List<RecruiterJobsDTO> getAll(){
        List<JobPostDTO> allFlatJobs = jobPostRepository.findAllJobs();


        Map<String, List<JobPostDTO>> jobsGroupedByRecruiterId = allFlatJobs.stream()
                .collect(Collectors.groupingBy(JobPostDTO::getUserId));


        List<RecruiterJobsDTO> recruiterJobsList = new ArrayList<>();
        for (Map.Entry<String, List<JobPostDTO>> entry : jobsGroupedByRecruiterId.entrySet()) {
            String recruiterUserId = entry.getKey();
            List<JobPostDTO> jobsForThisRecruiter = entry.getValue();
            String recruiterName = jobsForThisRecruiter.isEmpty() ? null : jobsForThisRecruiter.get(0).getName() ;
            String companyName=jobsForThisRecruiter.get(0).getCompanyName();
            RecruiterJobsDTO recruiterJobsDTO = new RecruiterJobsDTO(
                    recruiterUserId,
                    recruiterName,
                    jobsForThisRecruiter,
                    companyName
            );
            recruiterJobsList.add(recruiterJobsDTO);
        }

        return recruiterJobsList;
    }
}