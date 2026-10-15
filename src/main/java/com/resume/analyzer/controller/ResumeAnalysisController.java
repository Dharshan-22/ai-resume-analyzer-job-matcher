package com.resume.analyzer.controller;

import com.resume.analyzer.dto.AnalysisResponseDto;
import com.resume.analyzer.exception.InvalidResumeException;
import com.resume.analyzer.parser.ResumeParserService;
import com.resume.analyzer.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for handling resume uploads and dynamic job-match analysis.
 * Uploaded documents are parsed entirely in memory without writing files or text to disk or MySQL.
 */
@RestController
@RequestMapping("/api")
public class ResumeAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisController.class);

    public static final int MIN_JOB_DESCRIPTION_LENGTH = 15;
    public static final int MAX_JOB_DESCRIPTION_LENGTH = 20_000;

    private final ResumeParserService resumeParserService;
    private final AnalysisService analysisService;

    @Autowired
    public ResumeAnalysisController(ResumeParserService resumeParserService, AnalysisService analysisService) {
        this.resumeParserService = resumeParserService;
        this.analysisService = analysisService;
    }

    /**
     * Endpoint to upload a resume file (PDF/DOCX) and compare it dynamically with a target job description.
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeResume(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "jobDescription", required = false) String jobDescription,
            @RequestParam(value = "jobTitle", required = false) String jobTitle,
            @RequestParam(value = "company", required = false) String company) {

        // 1. Validation checks on request parameters
        if (file == null) {
            return badRequest("Please select or upload a resume file (PDF or DOCX).");
        }

        if (file.isEmpty() || file.getSize() == 0) {
            return badRequest("The uploaded resume file is empty. Please upload a valid document.");
        }

        // Job description validation: missing, empty, or whitespace-only
        if (jobDescription == null || jobDescription.trim().isEmpty()) {
            return badRequest("Please provide a job description. The job description cannot be empty or whitespace only.");
        }

        String trimmedJd = jobDescription.trim();

        // Job description minimum length validation (15 characters)
        if (trimmedJd.length() < MIN_JOB_DESCRIPTION_LENGTH) {
            return badRequest("Please provide a meaningful job description (at least " + MIN_JOB_DESCRIPTION_LENGTH + " characters).");
        }

        // Job description maximum length validation (20,000 characters)
        if (trimmedJd.length() > MAX_JOB_DESCRIPTION_LENGTH) {
            return badRequest("Job description exceeds the maximum allowed length of " + MAX_JOB_DESCRIPTION_LENGTH + " characters. Please provide a more concise job posting.");
        }

        try {
            // 2. In-memory text extraction from the uploaded file
            String extractedText = resumeParserService.extractText(file);

            // 3. Dynamic analysis & skill gap comparison
            AnalysisResponseDto result = analysisService.analyze(
                    file.getOriginalFilename(),
                    extractedText,
                    trimmedJd
            );

            return ResponseEntity.ok(result);

        } catch (InvalidResumeException ire) {
            return badRequest(ire.getMessage());
        } catch (Exception ex) {
            log.error("Unhandled error during resume analysis: {}", ex.getMessage(), ex);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", "An unexpected error occurred while analyzing the resume. Please check the file and try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return badRequest("File size exceeds the 5MB limit. Please upload a smaller resume document.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipartException(MultipartException ex) {
        return badRequest("Invalid upload request. Please ensure you are submitting a valid multipart file.");
    }

    @ExceptionHandler(InvalidResumeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidResume(InvalidResumeException ex) {
        return badRequest(ex.getMessage());
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("status", "BAD_REQUEST");
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }
}
