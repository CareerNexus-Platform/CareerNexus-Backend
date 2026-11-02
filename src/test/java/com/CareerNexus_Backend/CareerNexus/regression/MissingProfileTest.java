package com.CareerNexus_Backend.CareerNexus.regression;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.repository.*;
import com.CareerNexus_Backend.CareerNexus.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class MissingProfileTest {
 @Mock StudentRepository students;
 @Mock RecruiterRepository recruiters;
 @Mock TpoRepository officers;
 @InjectMocks StudentServices studentService;
 @InjectMocks RecruiterService recruiterService;
 @InjectMocks TpoService tpoService;
 @Test void missingStudentProfileHasNotFoundSemantics() { when(students.findById("new-user")).thenReturn(Optional.empty()); assertThrows(ResourceNotFoundException.class, () -> studentService.getProfileData("new-user")); }
 @Test void missingRecruiterProfileHasNotFoundSemantics() { when(recruiters.findById("new-user")).thenReturn(Optional.empty()); assertThrows(ResourceNotFoundException.class, () -> recruiterService.getProfileData("new-user")); }
 @Test void missingPlacementProfileHasNotFoundSemantics() { when(officers.findById("new-user")).thenReturn(Optional.empty()); assertThrows(ResourceNotFoundException.class, () -> tpoService.getProfileData("new-user")); }
}
