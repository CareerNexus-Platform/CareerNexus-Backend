package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.RecruiterDetailsDTO;

import com.CareerNexus_Backend.CareerNexus.dto.UserDTO;
import com.CareerNexus_Backend.CareerNexus.dto.UsersDTO;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.model.Recruiter; // Corrected import
import com.CareerNexus_Backend.CareerNexus.model.User;
import com.CareerNexus_Backend.CareerNexus.repository.RecruiterRepository; // Assuming this is your repo name
import com.CareerNexus_Backend.CareerNexus.repository.UserAuthRepository; // Assuming you use UserRepository for User entity
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Spring's Transactional
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class RecruiterService {

    private static final Logger logger = LoggerFactory.getLogger(RecruiterService.class);

    @Autowired
    private RecruiterRepository recruiterDetailsRepository;

    @Autowired
    private UserAuthRepository userRepository;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    /**
     * Checks if a recruiter profile exists for the given user.
     */
    public boolean isProfileMissing(UsersDTO user) {
        return recruiterDetailsRepository.findByUserId(user.getUserId()).isEmpty();
    }
    @Transactional
    public RecruiterDetailsDTO createOrUpdateProfile(String userId, RecruiterDetailsDTO recruiterDetailsDTO, MultipartFile img) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with ID: {}", userId);
                    return new ResourceNotFoundException("User not found with ID: " + userId);
                });

        Optional<Recruiter> recruiterDetail=this.getProfileForRecruiter(userId);

        Recruiter recruiterDetails;
        if (recruiterDetail.isPresent()) {

            recruiterDetails = recruiterDetail.get();

            recruiterDetails.setFirstName(recruiterDetailsDTO.getFirstName());
            recruiterDetails.setLastName(recruiterDetailsDTO.getLastName());
            recruiterDetails.setCompany(recruiterDetailsDTO.getCompany());
            recruiterDetails.setDesignation(recruiterDetailsDTO.getDesignation());
            recruiterDetails.setPhone(recruiterDetailsDTO.getPhone());
            try {
                // Upload new image to Supabase
                String imageUrl = img != null && !img.isEmpty() ? supabaseStorageService.uploadImage(img) : null;
                
                // Optionally delete old image if it's a Supabase URL
                if (imageUrl != null) supabaseStorageService.deleteImage(recruiterDetails.getImg_loc());
                
                if (imageUrl != null) recruiterDetails.setImg_loc(imageUrl);
                logger.info("Updated profile image for recruiter: {} in Supabase", userId);
            } catch (IOException e) {
                logger.error("Failed to update profile image for recruiter: {} in Supabase. Error: {}", userId, e.getMessage());
                throw e;
            }

        } else {
            try {
                String imageUrl = img != null && !img.isEmpty() ? supabaseStorageService.uploadImage(img) : null;
                logger.info("Saved new profile image for recruiter: {} in Supabase", userId);

                recruiterDetails = new Recruiter(
                        user,
                        recruiterDetailsDTO.getEmail(),
                        recruiterDetailsDTO.getFirstName(),
                        recruiterDetailsDTO.getLastName(),
                        recruiterDetailsDTO.getCompany(),
                        recruiterDetailsDTO.getDesignation(),
                        recruiterDetailsDTO.getPhone(),
                        imageUrl
                );
            } catch (IOException e) {
                logger.error("Failed to save new profile image for recruiter: {} in Supabase. Error: {}", userId, e.getMessage());
                throw e;
            }

        }

        return new RecruiterDetailsDTO( recruiterDetailsRepository.save(recruiterDetails));
    }

    @Transactional(readOnly = true) // Read-only transaction for fetching data
    public RecruiterDetailsDTO getProfileData(String userId) throws Exception { // userId is Long
        Recruiter recruiterDetails = recruiterDetailsRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("Recruiter profile not found for user ID: {}", userId);
                    return new ResourceNotFoundException("Complete your recruiter profile to get started.");
                });
        return new RecruiterDetailsDTO(recruiterDetails);
    }

    @jakarta.transaction.Transactional()
    public Optional<Recruiter> getProfileForRecruiter(String userId) {

        return recruiterDetailsRepository.findById(userId);

    }

    @jakarta.transaction.Transactional()
    public UserDTO getStudentProfileAsUserDTO(String userId) {
        Recruiter recruiterDetails = recruiterDetailsRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for User ID: " + userId));
        return new UserDTO(recruiterDetails);
    }

    public List<RecruiterDetailsDTO> getAllCompanies() {
        return recruiterDetailsRepository.findAllCompanies();
    }
}
