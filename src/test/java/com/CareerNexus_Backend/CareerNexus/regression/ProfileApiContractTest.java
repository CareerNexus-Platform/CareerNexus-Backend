package com.CareerNexus_Backend.CareerNexus.regression;

import com.CareerNexus_Backend.CareerNexus.controller.StudentController;
import com.CareerNexus_Backend.CareerNexus.dto.StudentDetailsDTO;
import com.CareerNexus_Backend.CareerNexus.exceptions.GlobalExceptionHandler;
import com.CareerNexus_Backend.CareerNexus.exceptions.ResourceNotFoundException;
import com.CareerNexus_Backend.CareerNexus.service.StudentServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProfileApiContractTest {
    @Mock StudentServices service;
    @InjectMocks StudentController controller;
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("student-1", null);
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @Test void missingProfileIsAnActionable404() throws Exception {
        when(service.getProfileData("student-1")).thenThrow(new ResourceNotFoundException("Complete your student profile to get started."));
        mvc.perform(get("/api/student/profile").principal(principal)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.error").value("Complete your student profile to get started."));
    }
    @Test void unexpectedStorageErrorsRemainServerErrors() throws Exception {
        when(service.getProfileData("student-1")).thenThrow(new IllegalStateException("Database unavailable"));
        mvc.perform(get("/api/student/profile").principal(principal)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("Something went wrong. Please try again."));
    }
    @Test void multipartProfileDoesNotRequireAPhoto() throws Exception {
        when(service.createOrUpdateProfile(any(), eq("student-1"), isNull())).thenReturn(new StudentDetailsDTO());
        MockMultipartFile data = new MockMultipartFile("data", "data.json", "application/json", "{\"firstName\":\"Asha\"}".getBytes());
        mvc.perform(multipart("/api/student/profile").file(data).principal(principal)).andExpect(status().isCreated());
        verify(service).createOrUpdateProfile(argThat(dto -> "Asha".equals(dto.getFirstName())), eq("student-1"), isNull());
    }
    @Test void quickApplyCanUpdateProfileWithJsonAndCannotOverrideTheAuthenticatedIdentity() throws Exception {
        when(service.createOrUpdateProfile(any(), eq("student-1"), isNull())).thenReturn(new StudentDetailsDTO());
        mvc.perform(post("/api/student/profile").principal(principal).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"other-user\",\"firstName\":\"Asha\"}")).andExpect(status().isOk());
        verify(service).createOrUpdateProfile(any(), eq("student-1"), isNull());
    }
    @Test void malformedJsonIsA400RatherThanA500() throws Exception {
        mvc.perform(post("/api/student/profile").principal(principal).contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
}
