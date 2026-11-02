package com.CareerNexus_Backend.CareerNexus.service;

import com.CareerNexus_Backend.CareerNexus.dto.CodeExecutionRequestDto;
import com.CareerNexus_Backend.CareerNexus.dto.CodeExecutionResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Service
public class CodeExecutionService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${rapidapi.key}")
    private String apiKey;

    private static final String ONECOMPILER_URL = "https://onecompiler-apis.p.rapidapi.com/api/v1/run";
    private static final String RAPIDAPI_HOST = "onecompiler-apis.p.rapidapi.com";

    /**
     * Sends the code to OneCompiler via RapidAPI and returns the results.
     */
    public CodeExecutionResponseDto executeCode(String language, String code, String stdin) {
        // 1. Prepare the File object
        String extension = getExtension(language);
        String filename = "java".equalsIgnoreCase(language) ? "Main.java" : "index." + extension;
        CodeExecutionRequestDto.CodeFile file = new CodeExecutionRequestDto.CodeFile(filename, code);

        // 2. Prepare the Request Body
        CodeExecutionRequestDto requestBody = new CodeExecutionRequestDto(
                language.toLowerCase(),
                stdin,
                Collections.singletonList(file)
        );

        // 3. Set Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-rapidapi-key", apiKey);
        headers.set("x-rapidapi-host", RAPIDAPI_HOST);

        HttpEntity<CodeExecutionRequestDto> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 4. Make the POST Request
            return restTemplate.postForObject(ONECOMPILER_URL, entity, CodeExecutionResponseDto.class);
        } catch (HttpClientErrorException e) {
            // Handle RapidAPI specific errors (401, 403, 429)
            String errorMsg = "Execution Service Error: ";
            if (e.getStatusCode().value() == 429) {
                errorMsg += "Rate limit exceeded. Please try again later.";
            } else if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                errorMsg += "Authentication failed with execution provider.";
            } else {
                errorMsg += e.getMessage();
            }
            throw new RuntimeException(errorMsg);
        } catch (RestClientException e) {
            throw new RuntimeException("Network error connecting to execution provider: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Code execution failed unexpectedly: " + e.getMessage());
        }
    }

    private String getExtension(String language) {
        return switch (language.toLowerCase()) {
            case "python" -> "py";
            case "java" -> "java";
            case "cpp", "c++" -> "cpp";
            case "c" -> "c";
            case "javascript" -> "js";
            default -> throw new IllegalArgumentException("Unknown language extension");
        };
    }
}
