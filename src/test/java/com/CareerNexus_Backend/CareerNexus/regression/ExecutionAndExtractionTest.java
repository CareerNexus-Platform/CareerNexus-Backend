package com.CareerNexus_Backend.CareerNexus.regression;

import com.CareerNexus_Backend.CareerNexus.dto.*;
import com.CareerNexus_Backend.CareerNexus.exceptions.ExternalServiceUnavailableException;
import com.CareerNexus_Backend.CareerNexus.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutionAndExtractionTest {
    @Test void javaExecutionUsesTheMainClassFilename() {
        RestTemplate http = mock(RestTemplate.class);
        CodeExecutionService service = new CodeExecutionService();
        ReflectionTestUtils.setField(service, "restTemplate", http);
        ReflectionTestUtils.setField(service, "apiKey", "test-only");
        when(http.postForObject(anyString(), any(HttpEntity.class), eq(CodeExecutionResponseDto.class))).thenAnswer(call -> {
            HttpEntity<?> entity = call.getArgument(1);
            CodeExecutionRequestDto body = (CodeExecutionRequestDto) entity.getBody();
            assertEquals("Main.java", body.getFiles().get(0).getName());
            return new CodeExecutionResponseDto();
        });
        service.executeCode("java", "class Main {}", "");
    }
    @Test void emptyExtractedQuestionSetsCannotCreateAnExam() {
        RestTemplate http = mock(RestTemplate.class);
        QuestionExtractionService service = new QuestionExtractionService(http, new ObjectMapper());
        ReflectionTestUtils.setField(service, "fastApiExtractUrl", "http://localhost/extract");
        when(http.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"questions\":[],\"answers\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> service.extractQuestionsFromFile(new ByteArrayResource(new byte[1])));
    }
    @Test void extractorFailureIsReportedAsUnavailable() {
        RestTemplate http = mock(RestTemplate.class);
        QuestionExtractionService service = new QuestionExtractionService(http, new ObjectMapper());
        ReflectionTestUtils.setField(service, "fastApiExtractUrl", "http://localhost/extract");
        when(http.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new org.springframework.web.client.ResourceAccessException("Service offline"));
        assertThrows(ExternalServiceUnavailableException.class, () -> service.extractQuestionsFromFile(new ByteArrayResource(new byte[1])));
    }
}
