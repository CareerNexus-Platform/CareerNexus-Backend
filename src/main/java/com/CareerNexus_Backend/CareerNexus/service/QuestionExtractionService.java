package com.CareerNexus_Backend.CareerNexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class QuestionExtractionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fastapi.extract.url:http://localhost:8000/assessment-extract}")
    private String fastApiExtractUrl;

    public QuestionExtractionService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String extractQuestionsFromFile(Resource fileResource) {
        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("assessment_data", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(fastApiExtractUrl, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseAndValidateResponse(response.getBody());
            }

            System.err.println("FastAPI returned non-OK status: " + response.getStatusCode());
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.ExternalServiceUnavailableException("Question extraction is unavailable. No assessment was created.");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error calling FastAPI: " + e.getMessage());
            throw new com.CareerNexus_Backend.CareerNexus.exceptions.ExternalServiceUnavailableException("Question extraction is unavailable. Start the assessment extraction service and try again.", e);
        }
    }

    /**
     * Strict validation + cleaning
     */
    private String parseAndValidateResponse(String responseBody) {
        try {

            // Remove everything before first {
            int startIndex = responseBody.indexOf("{");
            if (startIndex > 0) {
                responseBody = responseBody.substring(startIndex);
            }

            JsonNode root = objectMapper.readTree(responseBody);

            if (!root.has("questions") || !root.has("answers")) {
                System.err.println("Invalid JSON structure from FastAPI");
                throw new IllegalArgumentException("The document could not be read as multiple-choice questions.");
            }

            ArrayNode questions = (ArrayNode) root.get("questions");
            ArrayNode validQuestions = objectMapper.createArrayNode();

            // 🔥 Filter invalid questions (less than 4 options)
            for (JsonNode q : questions) {
                JsonNode options = q.get("options");
                if (options != null && options.isArray() && options.size() >= 4) {
                    validQuestions.add(q);
                }
            }

            // Replace with cleaned list
            ((com.fasterxml.jackson.databind.node.ObjectNode) root)
                    .set("questions", validQuestions);

            if (validQuestions.isEmpty() || !root.get("answers").isArray() || root.get("answers").isEmpty())
                throw new IllegalArgumentException("No complete questions and answers were found. Check the PDF and try again.");

            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);

        } catch (Exception e) {
            System.err.println("JSON Parsing Error: " + e.getMessage());
            throw new IllegalArgumentException("No complete questions and answers were found. Check the PDF and try again.", e);
        }
    }

}
