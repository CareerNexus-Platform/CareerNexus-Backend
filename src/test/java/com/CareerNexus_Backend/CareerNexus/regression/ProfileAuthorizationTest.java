package com.CareerNexus_Backend.CareerNexus.regression;
import com.CareerNexus_Backend.CareerNexus.config.WebSecurityConfig;
import com.CareerNexus_Backend.CareerNexus.controller.StudentController;
import com.CareerNexus_Backend.CareerNexus.dto.StudentDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.security.JwtFilter;
import com.CareerNexus_Backend.CareerNexus.security.JwtUtils;
import com.CareerNexus_Backend.CareerNexus.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers = StudentController.class, properties = {"server.port=0", "frontend.url=http://localhost:5173"})
@Import({WebSecurityConfig.class, JwtFilter.class})
class ProfileAuthorizationTest {
 @Autowired MockMvc mvc;
 @MockBean StudentServices students;
 @MockBean RecruiterService recruiters;
 @MockBean JwtUtils jwt;
 @MockBean CustomUserDetailsService users;
 @Test void anonymousProfileRequestsAskForSignIn() throws Exception { mvc.perform(get("/api/student/profile")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401)); verifyNoInteractions(students); }
 @Test @WithMockUser(username="student-1",roles="student") void studentsCanReadTheirOwnProfile() throws Exception { when(students.getProfileData("student-1")).thenReturn(new StudentDetailsDTO()); mvc.perform(get("/api/student/profile")).andExpect(status().isOk()); }
 @Test @WithMockUser(username="recruiter-1",roles="recruiter") void recruiterTokensCannotReadStudentProfiles() throws Exception { mvc.perform(get("/api/student/profile")).andExpect(status().isForbidden()); verifyNoInteractions(students); }
}
