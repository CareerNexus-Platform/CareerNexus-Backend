package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.StudentDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.dto.UserDTO;
import com.CareerNexus_Backend.CareerNexus.dto.UsersDTO;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.model.Student;
import com.CareerNexus_Backend.CareerNexus.model.User;
import com.CareerNexus_Backend.CareerNexus.repository.StudentRepository;
import com.CareerNexus_Backend.CareerNexus.repository.UserAuthRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import com.CareerNexus_Backend.CareerNexus.dto.ChangePasswordDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;

@Service
public class StudentServices {

    private static final Logger logger = LoggerFactory.getLogger(StudentServices.class);

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    /**
     * Checks if a student profile exists for the given user.
     * Note: Returns false if profile DOES exist (matching previous isStudentAvailable logic).
     * Refactored for better naming.
     */
    public boolean isProfileMissing(UsersDTO user) {
        return studentRepository.findByUserId(user.getUserId()).isEmpty();
    }

    @Transactional
    public StudentDetailsDTO createOrUpdateProfile(StudentDetailsDTO studentDetailsDTO,
                                                   String userId,
                                                   MultipartFile img) throws Exception {

        User user = userAuthRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with ID: {}", userId);
                    return new ResourceNotFoundException("User not found with ID: " + userId);
                });

        Optional<Student> studentDetail = this.getProfileForStudent(userId);

        Student studentDetails;

        if (studentDetail.isPresent()) {

            // 🔁 UPDATE EXISTING PROFILE
            studentDetails = studentDetail.get();

            studentDetails.setUser(user);
            studentDetails.setFirstName(studentDetailsDTO.getFirstName());
            studentDetails.setLastName(studentDetailsDTO.getLastName());
            studentDetails.setPhone(studentDetailsDTO.getPhone());
            studentDetails.setDepartment(studentDetailsDTO.getDepartment());
            studentDetails.setCGPA(studentDetailsDTO.getCgpa());
            studentDetails.setYear(studentDetailsDTO.getYear());
            studentDetails.setGraduationYear(studentDetailsDTO.getGraduationYear());
            studentDetails.setSkills(studentDetailsDTO.getSkills());
            studentDetails.setEmail(studentDetailsDTO.getEmail());

            // ✅ FIX: UPDATE URLS ALSO
            studentDetails.setUrls(studentDetailsDTO.getUrls());

            if (img != null && !img.isEmpty()) {
                try {
                    String imageUrl = supabaseStorageService.uploadImage(img);

                    if (studentDetails.getImg_loc() != null) {
                        supabaseStorageService.deleteImage(studentDetails.getImg_loc());
                    }

                    studentDetails.setImg_loc(imageUrl);

                    logger.info("Updated profile image for student: {} in Supabase", userId);

                } catch (IOException e) {
                    logger.error("Failed to update profile image for student: {} in Supabase. Error: {}", userId, e.getMessage());
                    throw e;
                }
            }

        } else {

            // 🆕 CREATE NEW PROFILE
            String imageUrl = null;

            if (img != null && !img.isEmpty()) {
                try {
                    imageUrl = supabaseStorageService.uploadImage(img);
                    logger.info("Saved new profile image for student: {} in Supabase", userId);
                } catch (IOException e) {
                    logger.error("Failed to save new profile image for student: {} in Supabase. Error: {}", userId, e.getMessage());
                    throw e;
                }
            }

            studentDetails = new Student(
                    studentDetailsDTO.getSkills(),
                    studentDetailsDTO.getEmail(),
                    studentDetailsDTO.getFirstName(),
                    studentDetailsDTO.getLastName(),
                    studentDetailsDTO.getDepartment(),
                    studentDetailsDTO.getCgpa(),
                    studentDetailsDTO.getPhone(),
                    studentDetailsDTO.getYear(),
                    studentDetailsDTO.getGraduationYear(),
                    studentDetailsDTO.getUrls(),
                    user,
                    imageUrl
            );
        }

        return new StudentDetailsDTO(studentRepository.save(studentDetails));
    }

    @Transactional
    public StudentDetailsDTO getProfileData(String userId) throws Exception {
        Student studentDetails = studentRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("Student profile not found for user ID: {}", userId);
                    return new ResourceNotFoundException("Complete your student profile to get started.");
                });

        return new StudentDetailsDTO(studentDetails);
    }

    @Transactional
    public Optional<Student> getProfileForStudent(String userId) {
        return studentRepository.findById(userId);
    }

    @Transactional
    public UserDTO getStudentProfileAsUserDTO(String userId) {
        Student studentDetails = studentRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for User ID: " + userId));

        return new UserDTO(studentDetails);
    }

    // ── Change Password ───────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<Map<String, String>> changePassword(
            String userId, ChangePasswordDTO request) {

        // Step 1 — Find the user in users table
        User user = userAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId));

        // Step 2 — Verify current password is correct
        if (!passwordEncoder.matches(
                request.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Current password is incorrect"));
        }

        // Step 3 — New password and confirm password must match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error",
                            "New password and confirm password do not match"));
        }

        // Step 4 — New password must not be same as current password
        if (passwordEncoder.matches(
                request.getNewPassword(), user.getPassword())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error",
                            "New password cannot be the same as current password"));
        }

        // Step 5 — Minimum length check
        if (request.getNewPassword().length() < 6) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error",
                            "New password must be at least 6 characters"));
        }

        // Step 6 — Encode and save new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userAuthRepository.save(user);

        logger.info("Password changed successfully for userId: {}", userId);

        return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully"));
    }
}
