package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.StudentDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.dto.TpoDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.dto.UsersDTO;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.model.TPO;
import com.CareerNexus_Backend.CareerNexus.model.User;
import com.CareerNexus_Backend.CareerNexus.repository.StudentRepository;
import com.CareerNexus_Backend.CareerNexus.repository.TpoRepository;
import com.CareerNexus_Backend.CareerNexus.repository.UserAuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TpoService {

    @Autowired
    private TpoRepository tpoRepository;

    @Autowired
    private UserAuthRepository userRepository; // To fetch the User entity to link with

    /**
     * Checks if a TPO profile exists for the given user.
     */
    public boolean isProfileMissing(UsersDTO user) {
        return tpoRepository.findByUserId(user.getUserId()).isEmpty();
    }

    @Autowired
    private StudentRepository studentRepository;


    @Transactional // Ensure this method runs in a single transaction
    public TpoDetailsDTO createOrUpdateProfile(String userId, TpoDetailsDTO tpoDetailsDTO) {


        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Optional<TPO> tpoDetail=this.getProfileForRecruiter(userId);

        TPO tpoDetails;


        if (tpoDetail.isPresent()) {

            tpoDetails = tpoDetail.get();
            tpoDetails.setFirstName(tpoDetailsDTO.getFirstName());
            tpoDetails.setLastName(tpoDetailsDTO.getLastName());
            tpoDetails.setPhone(tpoDetailsDTO.getPhone());

        } else {

            tpoDetails = new TPO(
                    user,
                    tpoDetailsDTO.getEmail(),
                    tpoDetailsDTO.getFirstName(),
                    tpoDetailsDTO.getLastName(),

                    tpoDetailsDTO.getPhone()
            );
        }

        return new TpoDetailsDTO(tpoRepository.save(tpoDetails));
    }

    @Transactional(readOnly = true)
    public TpoDetailsDTO getProfileData(String userId) {
        TPO tpoDetails = tpoRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Complete your placement officer profile to get started."));
        return new TpoDetailsDTO(tpoDetails);
    }

 @Transactional()
    public List<StudentDetailsDTO> getProfileLinks(String year, String department) {
        return studentRepository.findStudentsByYearAndDepartment(year,department);
    }


    @Transactional()
    public Optional<TPO> getProfileForRecruiter(String userId) {

        return tpoRepository.findById(userId);

    }
}
