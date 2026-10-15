package com.resume.analyzer;

import com.resume.analyzer.dto.AnalysisResponseDto;
import com.resume.analyzer.parser.ResumeParserService;
import com.resume.analyzer.service.AnalysisService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.resume.analyzer.controller.ResumeAnalysisController;
import com.resume.analyzer.exception.InvalidResumeException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ResumeAnalysisTest {

    private final ResumeParserService parserService = new ResumeParserService();
    private final AnalysisService analysisService = new AnalysisService();
    private final ResumeAnalysisController controller = new ResumeAnalysisController(parserService, analysisService);

    private static byte[] javaResumePdfBytes;
    private static byte[] pythonResumePdfBytes;
    private static byte[] davidResumeDocxBytes;

    private static final String TARGET_JAVA_JOB_DESCRIPTION =
            "We are seeking an ambitious Java Full Stack Developer.\n" +
            "Key Responsibilities:\n" +
            "- Design and maintain Java and Spring Boot microservices.\n" +
            "- Develop responsive interfaces using modern HTML5, CSS3, and JavaScript.\n" +
            "- Build robust REST APIs and interact with MySQL databases.\n" +
            "- Deploy using Docker, Kubernetes, and AWS cloud pipelines.\n" +
            "Requirements:\n" +
            "- Core Java, Spring Boot, Spring Data JPA, REST APIs, MySQL, Git, Maven.\n" +
            "- Exposure to Docker, Kubernetes, AWS, and CI/CD Pipelines.";

    @BeforeAll
    public static void setupSamplePdfs() throws IOException {
        new File("test-resumes").mkdirs();

        // 1. Generate Alex Mercer (Java Specialist) PDF
        javaResumePdfBytes = createSamplePdf(
                "Alex Mercer - Java Software Engineer",
                "Summary: 2 years of experience as a Backend Java Developer.",
                "Technical Skills: Java, Spring Boot, Spring Data JPA, MySQL, REST APIs, Git, Maven, Microservices.",
                "Experience: Developed RESTful microservices with Spring Boot and MySQL.",
                "Education: Bachelor of Science in Computer Science."
        );
        try (FileOutputStream fos = new FileOutputStream("test-resumes/alex_java_developer.pdf")) {
            fos.write(javaResumePdfBytes);
        }

        // 2. Generate Sarah Connor (Python & Cloud Specialist) PDF
        pythonResumePdfBytes = createSamplePdf(
                "Sarah Connor - Cloud & Python Engineer",
                "Summary: 3 years specializing in Python backend architecture and Cloud DevOps.",
                "Technical Skills: Python, Django, PostgreSQL, Docker, Kubernetes, AWS, Redis, Linux, CI/CD Pipelines.",
                "Experience: Orchestrated containerized deployments on AWS with Docker and Kubernetes.",
                "Education: Master of Science in Software Systems."
        );
        try (FileOutputStream fos = new FileOutputStream("test-resumes/sarah_python_cloud.pdf")) {
            fos.write(pythonResumePdfBytes);
        }

        // 3. Generate David Kim (Full Stack Java & React) DOCX
        davidResumeDocxBytes = createSampleDocx(
                "David Kim - Senior Java & Full Stack Engineer",
                "Summary: 4 years of experience building scalable enterprise web applications.",
                "Technical Skills: Java, Spring Boot, React, MySQL, REST APIs, Git, Docker, CI/CD Pipelines.",
                "Experience: Architected Spring Boot microservices integrated with React UI and MySQL databases.",
                "Education: Bachelor of Engineering in Software Engineering."
        );
        try (FileOutputStream fos = new FileOutputStream("test-resumes/david_java_fullstack.docx")) {
            fos.write(davidResumeDocxBytes);
        }
    }

    private static byte[] createSamplePdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                stream.newLineAtOffset(50, 700);

                for (String line : lines) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -25);
                }
                stream.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] createSampleDocx(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                XWPFParagraph p = document.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(text);
            }
            document.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Verify that distinct resumes extract distinct text and yield completely different matched/missing skills")
    public void testDynamicSkillExtractionAndScoring() {
        // --- 1. Test Java Resume ---
        MockMultipartFile javaFile = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes
        );
        String javaExtractedText = parserService.extractText(javaFile);
        assertNotNull(javaExtractedText);
        assertTrue(javaExtractedText.contains("Java"));
        assertTrue(javaExtractedText.contains("Spring Boot"));

        AnalysisResponseDto javaResult = analysisService.analyze(
                javaFile.getOriginalFilename(), javaExtractedText, TARGET_JAVA_JOB_DESCRIPTION
        );

        // --- 2. Test Python Cloud Resume ---
        MockMultipartFile pythonFile = new MockMultipartFile(
                "file", "sarah_python_cloud.pdf", "application/pdf", pythonResumePdfBytes
        );
        String pythonExtractedText = parserService.extractText(pythonFile);
        assertNotNull(pythonExtractedText);
        assertTrue(pythonExtractedText.contains("Python"));
        assertTrue(pythonExtractedText.contains("Docker"));

        AnalysisResponseDto pythonResult = analysisService.analyze(
                pythonFile.getOriginalFilename(), pythonExtractedText, TARGET_JAVA_JOB_DESCRIPTION
        );

        // --- 3. Assertions proving dynamic non-hardcoded behavior ---
        assertNotEquals(javaExtractedText, pythonExtractedText,
                "Extracted resume texts must be completely different");

        assertNotEquals(javaResult.getMatchedSkills(), pythonResult.getMatchedSkills(),
                "Matched skills must be different between candidates");

        assertNotEquals(javaResult.getMissingSkills(), pythonResult.getMissingSkills(),
                "Missing skills must be different between candidates");

        // Java developer must match Java & Spring Boot, but miss Docker & AWS
        assertTrue(javaResult.getMatchedSkills().contains("Java"));
        assertTrue(javaResult.getMatchedSkills().contains("Spring Boot"));
        assertTrue(javaResult.getMatchedSkills().contains("MySQL"));
        assertTrue(javaResult.getMissingSkills().contains("Docker"));
        assertTrue(javaResult.getMissingSkills().contains("AWS"));

        // Python developer must match Docker & AWS, but miss Java & Spring Boot
        assertTrue(pythonResult.getMatchedSkills().contains("Docker"));
        assertTrue(pythonResult.getMatchedSkills().contains("Kubernetes"));
        assertTrue(pythonResult.getMatchedSkills().contains("AWS"));
        assertTrue(pythonResult.getMissingSkills().contains("Java"));
        assertTrue(pythonResult.getMissingSkills().contains("Spring Boot"));
        assertTrue(pythonResult.getMissingSkills().contains("MySQL"));

        // Verify bonus/additional skills were detected
        assertTrue(pythonResult.getAdditionalSkills().contains("Python"));
        assertTrue(pythonResult.getAdditionalSkills().contains("PostgreSQL"));

        System.out.println("=== TEST SUCCESSFUL ===");
        System.out.println("Java Resume Matched: " + javaResult.getMatchedSkills());
        System.out.println("Java Resume Missing: " + javaResult.getMissingSkills());
        System.out.println("Python Resume Matched: " + pythonResult.getMatchedSkills());
        System.out.println("Python Resume Missing: " + pythonResult.getMissingSkills());
    }

    // =========================================================================
    // Phase 4 Step 1: Evidence Snippet Tests
    // =========================================================================

    @Test
    @DisplayName("Evidence snippet for a matched skill must contain actual resume text, not invented content")
    public void testEvidenceSnippetContainsActualResumeText() {
        // Java resume definitely contains "Java" and "Spring Boot"
        MockMultipartFile javaFile = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes
        );
        String resumeText = parserService.extractText(javaFile);

        Set<String> matched = analysisService.extractSkills(resumeText);
        Map<String, String> evidence = analysisService.extractEvidenceSnippets(resumeText, matched);

        // Java evidence must contain the word "Java" from the actual resume text
        assertTrue(matched.contains("Java"), "Java must be matched");
        String javaEvidence = evidence.get("Java");
        assertNotNull(javaEvidence, "Evidence map must contain a Java entry");
        assertNotEquals("Evidence not found", javaEvidence,
                "Evidence for Java must be found — resume explicitly contains Java");
        // The snippet must be a substring drawn from the resume text (case-insensitive keyword present)
        assertTrue(javaEvidence.toLowerCase().contains("java"),
                "Evidence snippet must contain the word 'java' from the resume");

        // Spring Boot evidence must contain 'spring' from the resume
        assertTrue(matched.contains("Spring Boot"), "Spring Boot must be matched");
        String sbEvidence = evidence.get("Spring Boot");
        assertNotNull(sbEvidence);
        assertNotEquals("Evidence not found", sbEvidence);
        assertTrue(sbEvidence.toLowerCase().contains("spring"),
                "Spring Boot evidence must contain 'spring' from the resume");

        System.out.println("=== EVIDENCE TEST 1 PASSED ===");
        System.out.println("Java Evidence   : " + javaEvidence);
        System.out.println("Spring Evidence : " + sbEvidence);
    }

    @Test
    @DisplayName("extractEvidenceSnippets returns 'Evidence not found' for a skill absent from resume text")
    public void testEvidenceNotFoundWhenSkillAbsentFromResume() {
        // Python resume does NOT mention Java
        MockMultipartFile pythonFile = new MockMultipartFile(
                "file", "sarah_python_cloud.pdf", "application/pdf", pythonResumePdfBytes
        );
        String resumeText = parserService.extractText(pythonFile);

        // Manually request evidence for "Java" even though it isn't in this resume
        Set<String> fakeMatched = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        fakeMatched.add("Java");

        Map<String, String> evidence = analysisService.extractEvidenceSnippets(resumeText, fakeMatched);

        String javaEvidence = evidence.get("Java");
        assertNotNull(javaEvidence, "Map must still have an entry even when skill is absent");
        assertEquals("Evidence not found", javaEvidence,
                "When 'Java' is not in the resume text, evidence must be 'Evidence not found'");

        System.out.println("=== EVIDENCE TEST 2 PASSED ===");
        System.out.println("Absent skill evidence: " + javaEvidence);
    }

    @Test
    @DisplayName("Different resumes produce different evidence snippets for the same skill (Docker)")
    public void testDifferentResumesProduceDifferentEvidence() {
        // Python cloud resume contains Docker; Java resume does NOT
        MockMultipartFile pythonFile = new MockMultipartFile(
                "file", "sarah_python_cloud.pdf", "application/pdf", pythonResumePdfBytes
        );
        MockMultipartFile javaFile = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes
        );

        String pythonText = parserService.extractText(pythonFile);
        String javaText   = parserService.extractText(javaFile);

        Set<String> dockerSet = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        dockerSet.add("Docker");

        Map<String, String> pythonEvidence = analysisService.extractEvidenceSnippets(pythonText, dockerSet);
        Map<String, String> javaEvidence   = analysisService.extractEvidenceSnippets(javaText,   dockerSet);

        // Python resume has Docker -> evidence found
        String pythonDockerEvidence = pythonEvidence.get("Docker");
        assertNotNull(pythonDockerEvidence);
        assertNotEquals("Evidence not found", pythonDockerEvidence,
                "Python/Cloud resume mentions Docker — evidence must be found");
        assertTrue(pythonDockerEvidence.toLowerCase().contains("docker"),
                "Python Docker evidence must contain the word 'docker'");

        // Java resume does NOT have Docker -> evidence not found
        String javaDockerEvidence = javaEvidence.get("Docker");
        assertNotNull(javaDockerEvidence);
        assertEquals("Evidence not found", javaDockerEvidence,
                "Java resume does not mention Docker — evidence must be 'Evidence not found'");

        // Confirm they are different
        assertNotEquals(pythonDockerEvidence, javaDockerEvidence,
                "Different resumes must produce different evidence for Docker");

        System.out.println("=== EVIDENCE TEST 3 PASSED ===");
        System.out.println("Python Docker Evidence: " + pythonDockerEvidence);
        System.out.println("Java   Docker Evidence: " + javaDockerEvidence);
    }

    // =========================================================================
    // Phase 4 Step 2: Required vs Preferred Skill Classification Tests
    // =========================================================================

    @Test
    @DisplayName("classifyJobSkills correctly splits skills under explicit Required / Preferred headings")
    public void testClassificationWithExplicitHeadings() {
        String jd =
            "We are looking for a Java developer.\n" +
            "\n" +
            "Requirements:\n" +
            "- Strong core Java and Spring Boot experience.\n" +
            "- MySQL database skills required.\n" +
            "- Git version control.\n" +
            "\n" +
            "Preferred:\n" +
            "- Docker and Kubernetes experience.\n" +
            "- AWS cloud knowledge.";

        AnalysisService.JobSkillClassification result = analysisService.classifyJobSkills(jd);

        assertTrue(result.isClassified(), "JD with explicit headings must be classified");

        // Required section must contain Java, Spring Boot, MySQL, Git
        assertTrue(result.requiredSkills().contains("Java"),        "Java must be required");
        assertTrue(result.requiredSkills().contains("Spring Boot"),  "Spring Boot must be required");
        assertTrue(result.requiredSkills().contains("MySQL"),        "MySQL must be required");
        assertTrue(result.requiredSkills().contains("Git"),          "Git must be required");

        // Preferred section must contain Docker, Kubernetes, AWS
        assertTrue(result.preferredSkills().contains("Docker"),      "Docker must be preferred");
        assertTrue(result.preferredSkills().contains("Kubernetes"),  "Kubernetes must be preferred");
        assertTrue(result.preferredSkills().contains("AWS"),         "AWS must be preferred");

        // No skill should appear in both sets
        for (String skill : result.requiredSkills()) {
            assertFalse(result.preferredSkills().contains(skill),
                    "Skill '" + skill + "' must not appear in both required and preferred");
        }

        System.out.println("=== CLASSIFICATION TEST 1 PASSED ===");
        System.out.println("Required : " + result.requiredSkills());
        System.out.println("Preferred: " + result.preferredSkills());
    }

    @Test
    @DisplayName("classifyJobSkills classifies skills via inline preferred phrases like 'exposure to' and 'strong plus'")
    public void testClassificationWithInlinePhrases() {
        String jd =
            "We need a backend developer.\n" +
            "- Java and Spring Boot are required.\n" +
            "- Exposure to Docker and Kubernetes is a strong plus.\n" +
            "- Familiarity with AWS is nice to have.";

        AnalysisService.JobSkillClassification result = analysisService.classifyJobSkills(jd);

        assertTrue(result.isClassified(), "JD with inline phrases must be classified");

        // Java and Spring Boot must be required (from 'are required' inline)
        assertTrue(result.requiredSkills().contains("Java"),        "Java must be required via inline phrase");
        assertTrue(result.requiredSkills().contains("Spring Boot"),  "Spring Boot must be required via inline phrase");

        // Docker/Kubernetes should be preferred (from 'exposure to … strong plus')
        assertTrue(result.preferredSkills().contains("Docker")
                || result.preferredSkills().contains("Kubernetes"),
                "Docker or Kubernetes must be preferred from 'strong plus' phrase");

        System.out.println("=== CLASSIFICATION TEST 2 PASSED ===");
        System.out.println("Required : " + result.requiredSkills());
        System.out.println("Preferred: " + result.preferredSkills());
    }

    @Test
    @DisplayName("Ambiguous JD with no headings or inline signals returns skillsClassified=false")
    public void testAmbiguousJdNotClassified() {
        // No headings, no 'must have', no 'strong plus', no 'exposure to' etc.
        String ambiguousJd =
            "We are looking for a developer with Java, Spring Boot, MySQL, and Git skills.";

        AnalysisService.JobSkillClassification result = analysisService.classifyJobSkills(ambiguousJd);

        // The ambiguous JD has no classification signals
        assertFalse(result.isClassified(),
                "JD with no heading or inline signals must not be classified");

        // All detected skills should land in required as safe default
        assertFalse(result.requiredSkills().isEmpty(),
                "Skills from ambiguous JD must fall into required as safe default");
        assertTrue(result.preferredSkills().isEmpty(),
                "Preferred set must be empty for ambiguous JD");

        System.out.println("=== CLASSIFICATION TEST 3 PASSED ===");
        System.out.println("Required (ambiguous, all skills): " + result.requiredSkills());
        System.out.println("Preferred (should be empty)      : " + result.preferredSkills());
    }

    @Test
    @DisplayName("analyze() returns correct missingRequiredSkills and missingPreferredSkills for the Java developer resume")
    public void testAnalyzePopulatesClassifiedMissingSkills() {
        String jdWithHeadings =
            "Java Full Stack Developer\n" +
            "\n" +
            "Requirements:\n" +
            "- Java and Spring Boot are essential for this role.\n" +
            "- MySQL database experience required.\n" +
            "- Git version control required.\n" +
            "\n" +
            "Preferred:\n" +
            "- Docker and Kubernetes experience preferred.\n" +
            "- AWS cloud knowledge preferred.";

        // Alex Mercer's Java resume — has Java, Spring Boot, MySQL, Git but NOT Docker/AWS
        MockMultipartFile javaFile = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes
        );
        String resumeText = parserService.extractText(javaFile);
        AnalysisResponseDto result = analysisService.analyze("alex_java_developer.pdf", resumeText, jdWithHeadings);

        assertTrue(result.isSkillsClassified(), "Result must be classified when JD has headings");

        // Java developer should have no missing required skills
        assertNotNull(result.getMissingRequiredSkills());
        assertTrue(result.getMissingRequiredSkills().isEmpty() ||
                   result.getMissingRequiredSkills().stream().noneMatch(
                           s -> s.equalsIgnoreCase("Java") || s.equalsIgnoreCase("Spring Boot")),
                "Java and Spring Boot must not be in missingRequired for the Java developer resume");

        // Docker and AWS should be missing preferred (not required)
        assertNotNull(result.getMissingPreferredSkills());
        assertTrue(result.getMissingPreferredSkills().contains("Docker"),
                "Docker must be in missingPreferredSkills, not missingRequired");
        assertTrue(result.getMissingPreferredSkills().contains("AWS"),
                "AWS must be in missingPreferredSkills, not missingRequired");

        // Verify Docker is NOT falsely categorized as a required missing skill
        assertFalse(result.getMissingRequiredSkills() != null &&
                    result.getMissingRequiredSkills().contains("Docker"),
                "Docker must not appear in missingRequiredSkills — it was listed under Preferred");

        // Existing fields still populated (backward compatibility)
        assertNotNull(result.getMatchedSkills());
        assertNotNull(result.getMissingSkills());
        assertFalse(result.getMatchedSkills().isEmpty(), "matchedSkills must still be populated");

        System.out.println("=== CLASSIFICATION TEST 4 PASSED ===");
        System.out.println("missingRequired : " + result.getMissingRequiredSkills());
        System.out.println("missingPreferred: " + result.getMissingPreferredSkills());
        System.out.println("matchedSkills   : " + result.getMatchedSkills());
    }

    // =========================================================================
    // Phase 4 Step 3: Skill-Matching Accuracy Regression Tests
    // =========================================================================

    @Test
    @DisplayName("REST APIs: legitimate matches still detected after false-positive fix")
    public void testRestApisLegitimateMatchesPreserved() {
        // All of these should match REST APIs
        String[] validRestTexts = {
            "Built RESTful APIs for a banking system",
            "Designed REST API endpoints with Spring Boot",
            "Developed REST APIs consumed by mobile clients",
            "Exposed REST services over HTTP",
            "Implemented Web APIs for third-party integration",
            "REST endpoints secured with JWT",
        };

        for (String text : validRestTexts) {
            Set<String> skills = analysisService.extractSkills(text);
            assertTrue(skills.contains("REST APIs"),
                    "Expected 'REST APIs' to match in: \"" + text + "\"");
        }
        System.out.println("=== REST VALID MATCHES TEST PASSED ===");
    }

    @Test
    @DisplayName("REST APIs: plain English 'rest' must NOT be detected as REST APIs skill")
    public void testRestFalsePositiveBlocked() {
        // None of these should match REST APIs
        String[] invalidRestTexts = {
            "I need a rest after a long day",
            "The rest of the team attended the meeting",
            "Please rest assured we will deliver",
            "He rested his case on the evidence",
            "Take a rest between interviews",
        };

        for (String text : invalidRestTexts) {
            Set<String> skills = analysisService.extractSkills(text);
            assertFalse(skills.contains("REST APIs"),
                    "Expected 'REST APIs' NOT to match in: \"" + text + "\"");
        }
        System.out.println("=== REST FALSE POSITIVE BLOCKED TEST PASSED ===");
    }

    @Test
    @DisplayName("Node.js / Express.js / React / Next.js: false positives on plain English words blocked")
    public void testFrameworkFalsePositivesBlocked() {
        // None of these generic sentences should trigger framework matches
        Map<String, String> falsePositiveCases = new java.util.LinkedHashMap<>();
        falsePositiveCases.put("Node.js", "A node in a distributed network graph");
        falsePositiveCases.put("Node.js", "Each tree node stores a value");
        falsePositiveCases.put("Express.js", "Please express your thoughts clearly");
        falsePositiveCases.put("Express.js", "The express train arrives at noon");
        falsePositiveCases.put("React", "I react well under pressure");
        falsePositiveCases.put("React", "The team should react to the feedback quickly");
        falsePositiveCases.put("Next.js", "The next step is to deploy the application");
        falsePositiveCases.put("Next.js", "We will discuss the next release on Monday");

        for (Map.Entry<String, String> entry : falsePositiveCases.entrySet()) {
            String skill = entry.getKey();
            String text  = entry.getValue();
            Set<String> skills = analysisService.extractSkills(text);
            assertFalse(skills.contains(skill),
                    "Expected '" + skill + "' NOT to match in: \"" + text + "\"");
        }
        System.out.println("=== FRAMEWORK FALSE POSITIVES BLOCKED TEST PASSED ===");
    }

    @Test
    @DisplayName("Node.js / Express.js / React / Next.js: legitimate tech mentions still matched")
    public void testFrameworkLegitimateMatchesPreserved() {
        Map<String, String> validCases = new java.util.LinkedHashMap<>();
        validCases.put("Node.js",    "Developed backend services with Node.js");
        validCases.put("Node.js",    "NodeJS REST API server");
        validCases.put("Express.js", "Used Express.js for routing");
        validCases.put("Express.js", "Built middleware with expressjs");
        validCases.put("React",      "Built UI with React.js components");
        validCases.put("React",      "ReactJS frontend application");
        validCases.put("React",      "React framework for SPA development");
        validCases.put("Next.js",    "Server-side rendering with Next.js");
        validCases.put("Next.js",    "Deployed a nextjs application");

        for (Map.Entry<String, String> entry : validCases.entrySet()) {
            String skill = entry.getKey();
            String text  = entry.getValue();
            Set<String> skills = analysisService.extractSkills(text);
            assertTrue(skills.contains(skill),
                    "Expected '" + skill + "' to match in: \"" + text + "\"");
        }
        System.out.println("=== FRAMEWORK LEGITIMATE MATCHES TEST PASSED ===");
    }

    @Test
    @DisplayName("CI/CD Pipelines: continuous delivery and continuous deployment now recognized")
    public void testCiCdExpandedAliases() {
        Map<String, Boolean> ciCdCases = new java.util.LinkedHashMap<>();
        // Should match
        ciCdCases.put("Set up CI/CD pipelines with Jenkins", true);
        ciCdCases.put("Implemented continuous integration workflows", true);
        ciCdCases.put("Automated continuous delivery to production", true);  // NEW
        ciCdCases.put("Managed continuous deployment on Kubernetes", true);   // NEW
        ciCdCases.put("Configured CI/CD pipeline using GitHub Actions", true);
        ciCdCases.put("Created deployment pipeline for microservices", true); // NEW

        for (Map.Entry<String, Boolean> entry : ciCdCases.entrySet()) {
            String text     = entry.getKey();
            boolean shouldMatch = entry.getValue();
            Set<String> skills  = analysisService.extractSkills(text);
            if (shouldMatch) {
                assertTrue(skills.contains("CI/CD Pipelines"),
                        "Expected 'CI/CD Pipelines' to match in: \"" + text + "\"");
            } else {
                assertFalse(skills.contains("CI/CD Pipelines"),
                        "Expected 'CI/CD Pipelines' NOT to match in: \"" + text + "\"");
            }
        }
        System.out.println("=== CI/CD EXPANDED ALIASES TEST PASSED ===");
    }

    @Test
    @DisplayName("Unit Testing: bare 'testing' word no longer triggers match; junit/mockito/unit tests still do")
    public void testUnitTestingFalsePositiveFixed() {
        // Should NOT match — plain English
        String[] noMatch = {
            "We will be testing the new product in Q4",
            "Testing is important in any organization",
            "Manual testing was performed by QA",
        };
        for (String text : noMatch) {
            Set<String> skills = analysisService.extractSkills(text);
            assertFalse(skills.contains("Unit Testing (JUnit/Mockito)"),
                    "Bare 'testing' must NOT match Unit Testing in: \"" + text + "\"");
        }

        // Should match — legitimate tech terms
        Map<String, String> validMatches = new java.util.LinkedHashMap<>();
        validMatches.put("Unit Testing (JUnit/Mockito)", "Wrote unit tests with JUnit 5");
        validMatches.put("Unit Testing (JUnit/Mockito)", "Used Mockito for mocking dependencies");
        validMatches.put("Unit Testing (JUnit/Mockito)", "Achieved 80% unit test coverage");
        validMatches.put("Unit Testing (JUnit/Mockito)", "Practiced TDD with integration tests");
        validMatches.put("Unit Testing (JUnit/Mockito)", "Built test automation suite");

        for (Map.Entry<String, String> entry : validMatches.entrySet()) {
            Set<String> skills = analysisService.extractSkills(entry.getValue());
            assertTrue(skills.contains(entry.getKey()),
                    "Expected '" + entry.getKey() + "' to match in: \"" + entry.getValue() + "\"");
        }
        System.out.println("=== UNIT TESTING FALSE POSITIVE FIXED TEST PASSED ===");
    }

    // =========================================================================
    // Phase 4 Step 4: Experience Scoring Tests
    // =========================================================================

    @Test
    @DisplayName("Experience Scoring: Exceeding explicit job requirement awards high score (>= 95)")
    public void testExperienceExceedingRequirement() {
        String jd = "We are seeking a developer with 2+ years of Java experience.";
        String resume = "Summary: 5 years of experience as a Backend Java Developer.\n" +
                "Experience: Developed high-throughput microservices using Spring Boot.\n" +
                "Education: Bachelor of Science in Computer Science.";

        int expScore = analysisService.calculateExperienceAlignment(resume, jd);
        assertTrue(expScore >= 95, "Candidate with 5 years for a 2+ year requirement should score >= 95, got: " + expScore);

        AnalysisService.ExperienceRequirement req = analysisService.extractRequiredExperience(jd);
        assertTrue(req.isSpecified(), "Job requirement must be specified");
        assertEquals(2.0, req.getMinYears(), 0.01, "Expected min requirement of 2.0 years");

        AnalysisService.CandidateExperience cand = analysisService.extractCandidateExperience(resume);
        assertTrue(cand.hasExplicitYears(), "Resume should have explicit years");
        assertTrue(cand.isRelevant(), "Resume experience must be marked as relevant tech experience");
        assertEquals(5.0, cand.getYears(), 0.01, "Expected 5.0 candidate years");

        System.out.println("=== EXPERIENCE EXCEEDING REQUIREMENT TEST PASSED (Score: " + expScore + ") ===");
    }

    @Test
    @DisplayName("Experience Scoring: Meeting explicit job requirement awards target score (88-92)")
    public void testExperienceMeetingRequirement() {
        String jd = "Requirements: 3+ years of experience in software development.";
        String resume = "Alex Mercer\n" +
                "Summary: 3 years of experience as a Software Engineer.\n" +
                "Projects: Developed REST APIs and microservices with Spring Boot.";

        int expScore = analysisService.calculateExperienceAlignment(resume, jd);
        assertTrue(expScore >= 88 && expScore <= 92,
                "Candidate with 3 years for 3-year requirement should score 88-92, got: " + expScore);

        System.out.println("=== EXPERIENCE MEETING REQUIREMENT TEST PASSED (Score: " + expScore + ") ===");
    }

    @Test
    @DisplayName("Experience Scoring: Falling short of requirement scales proportionally without arbitrary zeroing")
    public void testExperienceFallingShortOfRequirement() {
        String jd = "Minimum 4 years of software engineering experience required.";

        String resume2Yrs = "Summary: 2 years of experience as a Backend Java Developer.\n" +
                "Projects: Developed web applications and REST endpoints.";
        String resume1Yr = "Summary: 1 year of experience as a Web Developer.\n" +
                "Projects: Built frontend components.";

        int score2Yrs = analysisService.calculateExperienceAlignment(resume2Yrs, jd);
        int score1Yr = analysisService.calculateExperienceAlignment(resume1Yr, jd);

        assertTrue(score2Yrs >= 68 && score2Yrs <= 82,
                "2 years for 4-year requirement should score 68-82, got: " + score2Yrs);
        assertTrue(score1Yr >= 55 && score1Yr <= 65,
                "1 year for 4-year requirement should score 55-65, got: " + score1Yr);
        assertTrue(score2Yrs > score1Yr,
                "Candidate with 2 years must score higher than candidate with 1 year");

        System.out.println("=== EXPERIENCE SHORTFALL TEST PASSED (2yr: " + score2Yrs + ", 1yr: " + score1Yr + ") ===");
    }

    @Test
    @DisplayName("Experience Scoring: Missing experience duration when required handles candidates conservatively")
    public void testExperienceMissingWhenRequired() {
        String jd = "Requirements: at least 3 years of backend experience.";

        // Candidate with practical project evidence but no tenure stated
        String resumeProjectsOnly = "Education: Bachelor of Science in Computer Science.\n" +
                "Projects: Developed e-commerce backend microservices with Spring Boot and MySQL.";

        // Candidate with neither tenure nor technical project evidence
        String resumeEmpty = "Eager learner seeking opportunities in technology.";

        int scoreProjects = analysisService.calculateExperienceAlignment(resumeProjectsOnly, jd);
        int scoreEmpty = analysisService.calculateExperienceAlignment(resumeEmpty, jd);

        assertTrue(scoreProjects >= 45 && scoreProjects <= 55,
                "Project-only candidate should score conservative 45-55, got: " + scoreProjects);
        assertTrue(scoreEmpty >= 30 && scoreEmpty <= 40,
                "Candidate with no experience and no projects should score 30-40, got: " + scoreEmpty);
        assertTrue(scoreProjects > scoreEmpty,
                "Project evidence should score higher than completely empty experience");

        System.out.println("=== EXPERIENCE MISSING TEST PASSED (Projects: " + scoreProjects + ", Empty: " + scoreEmpty + ") ===");
    }

    @Test
    @DisplayName("Experience Scoring: Unrelated non-tech experience is not awarded technical experience points")
    public void testUnrelatedExperienceNotAwardedFullPoints() {
        String jd = "Requirements: 3+ years of software development experience.";
        String resumeUnrelated = "Summary: 5 years of experience as a Retail Sales Cashier and customer support representative.\n" +
                "Skills: Cash handling, customer care, inventory management.";

        int score = analysisService.calculateExperienceAlignment(resumeUnrelated, jd);
        assertTrue(score <= 45,
                "5 years in retail sales should not receive senior tech experience score (expected <= 45), got: " + score);

        AnalysisService.CandidateExperience cand = analysisService.extractCandidateExperience(resumeUnrelated);
        assertTrue(cand.hasExplicitYears(), "Has explicit years");
        assertFalse(cand.isRelevant(), "Retail sales must not be classified as relevant tech experience");

        System.out.println("=== UNRELATED EXPERIENCE TEST PASSED (Score: " + score + ") ===");
    }

    @Test
    @DisplayName("Experience Scoring: Ambiguous / unspecified JD uses fair neutral baseline without arbitrary penalties")
    public void testAmbiguousJobDescriptionNeutralScoring() {
        String ambiguousJd = "We are looking for a Java Developer with skills in Spring and MySQL.";

        String resume3Yrs = "Summary: 3 years of experience as a Backend Java Developer.\n" +
                "Projects: Developed REST microservices.";
        String resumeProjectsOnly = "Education: BS in Computer Science.\n" +
                "Projects: Built application using Spring Boot.";
        String resumeEmpty = "Looking for software jobs.";

        int score3Yrs = analysisService.calculateExperienceAlignment(resume3Yrs, ambiguousJd);
        int scoreProjects = analysisService.calculateExperienceAlignment(resumeProjectsOnly, ambiguousJd);
        int scoreEmpty = analysisService.calculateExperienceAlignment(resumeEmpty, ambiguousJd);

        assertEquals(90, score3Yrs, "3-year candidate on neutral JD should score 90");
        assertEquals(75, scoreProjects, "Projects-only candidate on neutral JD should score neutral baseline 75");
        assertEquals(60, scoreEmpty, "Empty candidate on neutral JD should score baseline 60");

        System.out.println("=== AMBIGUOUS JD NEUTRAL SCORING TEST PASSED (3yr: " + score3Yrs +
                ", Projects: " + scoreProjects + ", Empty: " + scoreEmpty + ") ===");
    }

    @Test
    @DisplayName("Experience Extraction: En-dashes, ranges, and word numbers are parsed accurately")
    public void testExperienceParsingVariants() {
        // En-dash range in JD
        String jdEnDash = "Seeking an engineer with 1–3 years of experience.";
        AnalysisService.ExperienceRequirement reqEnDash = analysisService.extractRequiredExperience(jdEnDash);
        assertTrue(reqEnDash.isSpecified());
        assertEquals(1.0, reqEnDash.getMinYears(), 0.01);
        assertEquals(3.0, reqEnDash.getMaxYears(), 0.01);

        // Word numbers in JD
        String jdWord = "Minimum two years of experience required.";
        AnalysisService.ExperienceRequirement reqWord = analysisService.extractRequiredExperience(jdWord);
        assertTrue(reqWord.isSpecified());
        assertEquals(2.0, reqWord.getMinYears(), 0.01);

        // Word numbers in resume
        String resumeWord = "Summary: three years of experience as a Software Developer.";
        AnalysisService.CandidateExperience candWord = analysisService.extractCandidateExperience(resumeWord);
        assertTrue(candWord.hasExplicitYears());
        assertTrue(candWord.isRelevant());
        assertEquals(3.0, candWord.getYears(), 0.01);

        System.out.println("=== EXPERIENCE PARSING VARIANTS TEST PASSED ===");
    }

    // =========================================================================
    // PHASE 5 STEP 1: RESUME UPLOAD VALIDATION & ERROR HANDLING TESTS
    // =========================================================================

    @Test
    @DisplayName("Validation: Missing or null resume file is rejected with clear error")
    public void testMissingFileValidation() {
        // Parser level
        InvalidResumeException parserEx = assertThrows(InvalidResumeException.class, () ->
                parserService.extractText(null));
        assertTrue(parserEx.getMessage().toLowerCase().contains("no resume file was uploaded"),
                "Parser error should mention no resume file was uploaded");

        // Controller level
        ResponseEntity<?> response = controller.analyzeResume(null, TARGET_JAVA_JOB_DESCRIPTION, "Java Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").contains("Please select or upload a resume file"));

        System.out.println("=== MISSING FILE VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Validation: Empty (0-byte) resume file is rejected with clear error")
    public void testEmptyFileValidation() {
        MockMultipartFile emptyPdf = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        MockMultipartFile emptyDocx = new MockMultipartFile("file", "empty.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[0]);

        // Parser level
        InvalidResumeException pdfEx = assertThrows(InvalidResumeException.class, () ->
                parserService.extractText(emptyPdf));
        assertTrue(pdfEx.getMessage().toLowerCase().contains("empty"),
                "Expected message about empty file, got: " + pdfEx.getMessage());

        InvalidResumeException docxEx = assertThrows(InvalidResumeException.class, () ->
                parserService.extractText(emptyDocx));
        assertTrue(docxEx.getMessage().toLowerCase().contains("empty"));

        // Controller level
        ResponseEntity<?> response = controller.analyzeResume(emptyPdf, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").toLowerCase().contains("empty"));

        System.out.println("=== EMPTY FILE VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Validation: Unsupported file formats (.txt, .exe, .png, invalid filename) are safely rejected")
    public void testUnsupportedFileTypeValidation() {
        MockMultipartFile txtFile = new MockMultipartFile("file", "resume.txt", "text/plain", "Java Spring Boot developer".getBytes());
        MockMultipartFile exeFile = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "dummy binary content".getBytes());
        MockMultipartFile pngFile = new MockMultipartFile("file", "resume.png", "image/png", "fake png data".getBytes());
        MockMultipartFile noExtFile = new MockMultipartFile("file", "myresume", "application/octet-stream", "fake data".getBytes());

        for (MockMultipartFile file : new MockMultipartFile[]{txtFile, exeFile, pngFile, noExtFile}) {
            InvalidResumeException ex = assertThrows(InvalidResumeException.class, () ->
                    parserService.extractText(file));
            assertTrue(ex.getMessage().toLowerCase().contains("unsupported file") || ex.getMessage().toLowerCase().contains("valid filename"),
                    "Expected unsupported file format message for " + file.getOriginalFilename() + ", got: " + ex.getMessage());
        }

        // Controller level
        ResponseEntity<?> response = controller.analyzeResume(txtFile, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").toLowerCase().contains("unsupported"));

        System.out.println("=== UNSUPPORTED FILE TYPE VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Validation: Corrupted PDF file with invalid signature is safely detected and rejected")
    public void testCorruptedPdfFileValidation() {
        byte[] fakePdfBytes = "This is not a real PDF document, just text disguised with a pdf extension.".getBytes();
        MockMultipartFile corruptPdf = new MockMultipartFile("file", "fake_resume.pdf", "application/pdf", fakePdfBytes);

        // Parser level
        InvalidResumeException ex = assertThrows(InvalidResumeException.class, () ->
                parserService.extractText(corruptPdf));
        assertTrue(ex.getMessage().toLowerCase().contains("corrupted") || ex.getMessage().toLowerCase().contains("invalid"),
                "Expected corruption error message, got: " + ex.getMessage());

        // Controller level
        ResponseEntity<?> response = controller.analyzeResume(corruptPdf, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").toLowerCase().contains("corrupted") || body.get("message").toLowerCase().contains("invalid"));

        System.out.println("=== CORRUPTED PDF VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Validation: Corrupted DOCX file with invalid ZIP signature is safely detected and rejected")
    public void testCorruptedDocxFileValidation() {
        byte[] fakeDocxBytes = "This is plain text with a docx extension, missing ZIP magic bytes.".getBytes();
        MockMultipartFile corruptDocx = new MockMultipartFile("file", "fake_resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", fakeDocxBytes);

        // Parser level
        InvalidResumeException ex = assertThrows(InvalidResumeException.class, () ->
                parserService.extractText(corruptDocx));
        assertTrue(ex.getMessage().toLowerCase().contains("corrupted") || ex.getMessage().toLowerCase().contains("invalid"),
                "Expected corruption error message, got: " + ex.getMessage());

        // Controller level
        ResponseEntity<?> response = controller.analyzeResume(corruptDocx, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").toLowerCase().contains("corrupted") || body.get("message").toLowerCase().contains("invalid"));

        System.out.println("=== CORRUPTED DOCX VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Validation: Blank PDF document with no extractable text triggers empty-text guidance")
    public void testBlankPdfEmptyExtractedTextValidation() throws IOException {
        // Create a genuine PDF document with 1 page, but without any text
        byte[] blankPdfBytes;
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(baos);
            blankPdfBytes = baos.toByteArray();
        }

        MockMultipartFile blankPdfFile = new MockMultipartFile(
                "file", "blank_scanned_resume.pdf", "application/pdf", blankPdfBytes);

        // Parser level
        InvalidResumeException ex = assertThrows(InvalidResumeException.class, () ->
                parserService.extractText(blankPdfFile));
        assertTrue(ex.getMessage().toLowerCase().contains("no readable text could be extracted"),
                "Expected message about no readable text, got: " + ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("scanned"),
                "Expected guidance for scanned document, got: " + ex.getMessage());

        // Controller level
        ResponseEntity<?> response = controller.analyzeResume(blankPdfFile, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").toLowerCase().contains("no readable text"));

        System.out.println("=== BLANK PDF EMPTY TEXT TEST PASSED ===");
    }

    @Test
    @DisplayName("Controller: Validates job description length and successfully processes valid resume upload")
    public void testControllerValidationAndSuccessfulAnalysisFlow() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // 1. Missing or too short job description (< 15 chars)
        ResponseEntity<?> shortJdResp = controller.analyzeResume(validJavaPdf, "too short", "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, shortJdResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> shortJdBody = (Map<String, String>) shortJdResp.getBody();
        assertEquals("BAD_REQUEST", shortJdBody.get("status"));
        assertTrue(shortJdBody.get("message").contains("at least 15 characters"));

        // 2. Successful analysis request
        ResponseEntity<?> successResp = controller.analyzeResume(
                validJavaPdf, TARGET_JAVA_JOB_DESCRIPTION, "Java Developer", "Tech Corp");
        assertEquals(HttpStatus.OK, successResp.getStatusCode());
        assertNotNull(successResp.getBody());
        assertTrue(successResp.getBody() instanceof AnalysisResponseDto);

        AnalysisResponseDto dto = (AnalysisResponseDto) successResp.getBody();
        assertEquals("alex_java_developer.pdf", dto.getFileName());
        assertNotNull(dto.getMatchedSkills());
        assertTrue(dto.getMatchedSkills().contains("Java"));
        assertTrue(dto.getMatchedSkills().contains("Spring Boot"));
        assertNotNull(dto.getSkillEvidence());
        assertTrue(dto.getMatchScore() > 0);

        System.out.println("=== CONTROLLER VALIDATION & SUCCESS TEST PASSED (Score: " + dto.getMatchScore() + "%) ===");
    }

    // =========================================================================
    // PHASE 5 STEP 2: JOB DESCRIPTION VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("JD Validation: Missing (null) and empty job descriptions return 400 Bad Request")
    public void testJobDescriptionMissingAndEmptyValidation() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // 1. null job description
        ResponseEntity<?> nullJdResp = controller.analyzeResume(validJavaPdf, null, "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, nullJdResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> nullBody = (Map<String, String>) nullJdResp.getBody();
        assertEquals("BAD_REQUEST", nullBody.get("status"));
        assertTrue(nullBody.get("message").toLowerCase().contains("cannot be empty or whitespace only"));

        // 2. empty ("") job description
        ResponseEntity<?> emptyJdResp = controller.analyzeResume(validJavaPdf, "", "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, emptyJdResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> emptyBody = (Map<String, String>) emptyJdResp.getBody();
        assertEquals("BAD_REQUEST", emptyBody.get("status"));
        assertTrue(emptyBody.get("message").toLowerCase().contains("cannot be empty or whitespace only"));

        System.out.println("=== JD MISSING & EMPTY VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("JD Validation: Whitespace-only job descriptions return 400 Bad Request")
    public void testJobDescriptionWhitespaceOnlyValidation() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        String whitespaceJd = "   \n\t   \r\n   ";
        ResponseEntity<?> response = controller.analyzeResume(validJavaPdf, whitespaceJd, "Dev", "Acme");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").toLowerCase().contains("cannot be empty or whitespace only"),
                "Expected empty/whitespace message, got: " + body.get("message"));

        System.out.println("=== JD WHITESPACE-ONLY VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("JD Validation: Job descriptions below 15 characters return 400 Bad Request")
    public void testJobDescriptionTooShortValidation() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // 14 characters (1 below minimum)
        String fourteenChars = "12345678901234";
        assertEquals(14, fourteenChars.length());

        ResponseEntity<?> response14 = controller.analyzeResume(validJavaPdf, fourteenChars, "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, response14.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body14 = (Map<String, String>) response14.getBody();
        assertEquals("BAD_REQUEST", body14.get("status"));
        assertTrue(body14.get("message").contains("at least 15 characters"));

        // Short string padded with whitespace: "   Junior Dev   " (trimmed length 10)
        ResponseEntity<?> paddedShortResp = controller.analyzeResume(validJavaPdf, "   Junior Dev   ", "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, paddedShortResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> paddedBody = (Map<String, String>) paddedShortResp.getBody();
        assertTrue(paddedBody.get("message").contains("at least 15 characters"));

        System.out.println("=== JD TOO-SHORT VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("JD Validation: Job descriptions exceeding 20,000 characters return 400 Bad Request")
    public void testJobDescriptionTooLongValidation() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // Build a job description exceeding 20,000 characters (20,001 chars)
        StringBuilder longJd = new StringBuilder();
        while (longJd.length() < 20_001) {
            longJd.append("We are looking for an experienced Java developer with Spring Boot, MySQL, and microservices experience.\n");
        }
        assertTrue(longJd.length() > ResumeAnalysisController.MAX_JOB_DESCRIPTION_LENGTH);

        ResponseEntity<?> response = controller.analyzeResume(validJavaPdf, longJd.toString(), "Senior Dev", "Enterprise Corp");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").contains("exceeds the maximum allowed length of 20000 characters"),
                "Expected max length exceeded message, got: " + body.get("message"));

        System.out.println("=== JD TOO-LONG VALIDATION TEST PASSED (Length: " + longJd.length() + ") ===");
    }

    @Test
    @DisplayName("JD Validation: Boundary values (exactly 15 and 20,000 characters) are accepted and processed")
    public void testJobDescriptionBoundaryMinAndMaxValid() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // 1. Exactly 15 characters
        String exactly15Chars = "Java Developer!";
        assertEquals(15, exactly15Chars.length());

        ResponseEntity<?> minValidResp = controller.analyzeResume(validJavaPdf, exactly15Chars, "Dev", "Acme");
        assertEquals(HttpStatus.OK, minValidResp.getStatusCode());
        assertTrue(minValidResp.getBody() instanceof AnalysisResponseDto);

        // 2. Exactly 20,000 characters
        String base = "We need an experienced Java and Spring Boot engineer with MySQL and REST APIs. ";
        StringBuilder maxSb = new StringBuilder(base);
        while (maxSb.length() < ResumeAnalysisController.MAX_JOB_DESCRIPTION_LENGTH) {
            maxSb.append("Additional engineering responsibilities and specifications included. ");
        }
        String exactly20kChars = maxSb.substring(0, ResumeAnalysisController.MAX_JOB_DESCRIPTION_LENGTH);
        assertEquals(20_000, exactly20kChars.length());

        ResponseEntity<?> maxValidResp = controller.analyzeResume(validJavaPdf, exactly20kChars, "Lead Dev", "Acme");
        assertEquals(HttpStatus.OK, maxValidResp.getStatusCode());
        assertTrue(maxValidResp.getBody() instanceof AnalysisResponseDto);

        AnalysisResponseDto maxDto = (AnalysisResponseDto) maxValidResp.getBody();
        assertTrue(maxDto.getMatchedSkills().contains("Java"));
        assertTrue(maxDto.getMatchedSkills().contains("Spring Boot"));

        System.out.println("=== JD BOUNDARY MIN & MAX VALIDATION TEST PASSED ===");
    }

    @Test
    @DisplayName("JD Validation: Valid job descriptions preserve all skill matching, evidence, classification, and scoring")
    public void testJobDescriptionPreservesAllFeatures() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        String classifiedJd = "We are seeking a Java Full Stack Developer with 2+ years of experience.\n" +
                "Requirements:\n" +
                "- Java, Spring Boot, MySQL, REST APIs, Git\n" +
                "Preferred Qualifications:\n" +
                "- Docker, Kubernetes, AWS";

        ResponseEntity<?> response = controller.analyzeResume(validJavaPdf, classifiedJd, "Full Stack Dev", "Cloud Corp");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof AnalysisResponseDto);

        AnalysisResponseDto dto = (AnalysisResponseDto) response.getBody();

        // 1. Dynamic skills matching
        assertTrue(dto.getMatchedSkills().contains("Java"));
        assertTrue(dto.getMatchedSkills().contains("Spring Boot"));
        assertTrue(dto.getMatchedSkills().contains("MySQL"));

        // 2. Evidence snippets
        assertNotNull(dto.getSkillEvidence());
        assertTrue(dto.getSkillEvidence().containsKey("Java"));
        assertNotEquals("Evidence not found", dto.getSkillEvidence().get("Java"));

        // 3. Required / Preferred classification
        assertTrue(dto.isSkillsClassified());
        assertTrue(dto.getRequiredMatchedSkills().contains("Java"));
        assertTrue(dto.getRequiredMatchedSkills().contains("Spring Boot"));
        assertTrue(dto.getMissingPreferredSkills().contains("Docker"));

        // 4. Experience & Match score
        assertTrue(dto.getMatchScore() > 50);
        assertNotNull(dto.getScoreBreakdown());
        assertTrue(dto.getScoreBreakdown().containsKey("Experience Alignment"));

        // 5. Recommendations
        assertNotNull(dto.getRecommendations());
        assertFalse(dto.getRecommendations().isEmpty());

        System.out.println("=== JD PRESERVES ALL FEATURES TEST PASSED (Score: " + dto.getMatchScore() + "%) ===");
    }

    // =========================================================================
    // PHASE 5 STEP 3: LOADING STATES & FAILURE RECOVERY RESILIENCE TESTS
    // =========================================================================

    @Test
    @DisplayName("Exception Handling: MaxUploadSizeExceededException returns safe 400 Bad Request JSON")
    public void testControllerExceptionHandlerMaxUploadSize() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5 * 1024 * 1024);
        ResponseEntity<Map<String, String>> response = controller.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().get("status"));
        assertTrue(response.getBody().get("message").contains("5MB limit"));

        System.out.println("=== EXCEPTION HANDLER MAX UPLOAD SIZE TEST PASSED ===");
    }

    @Test
    @DisplayName("Exception Handling: MultipartException returns safe 400 Bad Request JSON")
    public void testControllerExceptionHandlerMultipartException() {
        MultipartException ex = new MultipartException("Malformed multipart request");
        ResponseEntity<Map<String, String>> response = controller.handleMultipartException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().get("status"));
        assertTrue(response.getBody().get("message").contains("valid multipart file"));

        System.out.println("=== EXCEPTION HANDLER MULTIPART TEST PASSED ===");
    }

    @Test
    @DisplayName("Exception Handling: InvalidResumeException returns safe 400 Bad Request JSON with exception message")
    public void testControllerExceptionHandlerInvalidResume() {
        InvalidResumeException ex = new InvalidResumeException("Encrypted document cannot be processed.");
        ResponseEntity<Map<String, String>> response = controller.handleInvalidResume(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().get("status"));
        assertEquals("Encrypted document cannot be processed.", response.getBody().get("message"));

        System.out.println("=== EXCEPTION HANDLER INVALID RESUME TEST PASSED ===");
    }

    @Test
    @DisplayName("Failure Recovery: Consecutive requests isolate state and cleanly recover from failures")
    public void testConsecutiveAnalysesStateIsolation() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);
        MockMultipartFile corruptPdf = new MockMultipartFile(
                "file", "bad.pdf", "application/pdf", new byte[]{1, 2, 3});
        MockMultipartFile validPythonPdf = new MockMultipartFile(
                "file", "sarah_python_cloud.pdf", "application/pdf", pythonResumePdfBytes);

        // 1. First request succeeds
        ResponseEntity<?> resp1 = controller.analyzeResume(validJavaPdf, TARGET_JAVA_JOB_DESCRIPTION, "Java Dev", "Acme");
        assertEquals(HttpStatus.OK, resp1.getStatusCode());
        AnalysisResponseDto dto1 = (AnalysisResponseDto) resp1.getBody();
        assertNotNull(dto1);
        assertTrue(dto1.getMatchedSkills().contains("Java"));

        // 2. Second request fails (corrupted file)
        ResponseEntity<?> resp2 = controller.analyzeResume(corruptPdf, TARGET_JAVA_JOB_DESCRIPTION, "Java Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, resp2.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body2 = (Map<String, String>) resp2.getBody();
        assertEquals("BAD_REQUEST", body2.get("status"));

        // 3. Third request succeeds cleanly without state bleed from previous requests
        ResponseEntity<?> resp3 = controller.analyzeResume(validPythonPdf, TARGET_JAVA_JOB_DESCRIPTION, "Cloud Dev", "Acme");
        assertEquals(HttpStatus.OK, resp3.getStatusCode());
        AnalysisResponseDto dto3 = (AnalysisResponseDto) resp3.getBody();
        assertNotNull(dto3);
        assertEquals("sarah_python_cloud.pdf", dto3.getFileName());
        assertTrue(dto3.getMatchedSkills().contains("Docker"));
        // Confirm Sarah does NOT leak Alex's Java match
        assertFalse(dto3.getMatchedSkills().contains("Java"), "Second resume must not inherit skills from first request");

        System.out.println("=== CONSECUTIVE ANALYSES STATE ISOLATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Failure Recovery: Faulty I/O returns safe Bad Request without crashing")
    public void testServerFailureRecoverySafeResponse() {
        // MockMultipartFile that throws an IOException when getBytes() is invoked
        MockMultipartFile faultyFile = new MockMultipartFile("file", "broken.pdf", "application/pdf", new byte[]{1, 2}) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("Simulated disk/memory I/O failure");
            }
        };

        ResponseEntity<?> response = controller.analyzeResume(faultyFile, TARGET_JAVA_JOB_DESCRIPTION, "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("BAD_REQUEST", body.get("status"));
        assertTrue(body.get("message").contains("Unable to read uploaded file data"));

        System.out.println("=== SERVER FAILURE RECOVERY TEST PASSED ===");
    }

    // =========================================================================
    // PHASE 5 STEP 4: UX CONSISTENCY & DTO INTEGRITY TESTS
    // =========================================================================

    @Test
    @DisplayName("UX Consistency: Analysis response guarantees complete DTO contracts for frontend dashboard rendering")
    public void testUxResponseDtoContractIntegrity() {
        MockMultipartFile validJavaPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        String sampleJd = "We need a Java developer with Spring Boot, MySQL, and Docker experience.\n" +
                "Requirements:\n" +
                "- Java, Spring Boot, MySQL\n" +
                "Preferred:\n" +
                "- Docker, AWS";

        ResponseEntity<?> response = controller.analyzeResume(validJavaPdf, sampleJd, "Full Stack Engineer", "Global Tech");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof AnalysisResponseDto);

        AnalysisResponseDto dto = (AnalysisResponseDto) response.getBody();

        // UI extraction proof contract
        assertNotNull(dto.getFileName(), "File name must be provided for UI extraction proof");
        assertTrue(dto.getResumeCharCount() > 0, "Character count must be positive");
        assertTrue(dto.getResumeWordCount() > 0, "Word count must be positive");
        assertNotNull(dto.getResumeSnippet(), "Resume snippet must not be null");
        assertFalse(dto.getResumeSnippet().isBlank(), "Resume snippet must contain extracted text proof");

        // UI score & rating contract
        assertTrue(dto.getMatchScore() >= 0 && dto.getMatchScore() <= 100, "Match score must be bounded 0-100");
        assertNotNull(dto.getRating(), "Score rating label must be present");

        // UI sub-metrics breakdown contract
        assertNotNull(dto.getScoreBreakdown(), "Score breakdown map must be present");
        assertTrue(dto.getScoreBreakdown().containsKey("Required Skills Match"));
        assertTrue(dto.getScoreBreakdown().containsKey("Experience Alignment"));
        assertTrue(dto.getScoreBreakdown().containsKey("Education & Qualifications"));
        assertTrue(dto.getScoreBreakdown().containsKey("Job Keyword Alignment"));

        // UI skills cloud contract
        assertNotNull(dto.getMatchedSkills());
        assertNotNull(dto.getMissingSkills());
        assertNotNull(dto.getAdditionalSkills());
        assertNotNull(dto.getSkillEvidence());

        // UI recommendations contract
        assertNotNull(dto.getRecommendations());
        assertFalse(dto.getRecommendations().isEmpty());

        System.out.println("=== UX RESPONSE DTO CONTRACT INTEGRITY TEST PASSED ===");
    }

    // =========================================================================
    // PHASE 6 STEP 1: FINAL END-TO-END TEST SUITE
    // =========================================================================

    @Test
    @DisplayName("E2E Scenario 1: Successful analysis using a valid PDF resume")
    public void testE2eScenario1ValidPdfAnalysis() {
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        ResponseEntity<?> response = controller.analyzeResume(
                validPdf, TARGET_JAVA_JOB_DESCRIPTION, "Java Developer", "Enterprise Corp");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof AnalysisResponseDto);

        AnalysisResponseDto dto = (AnalysisResponseDto) response.getBody();
        assertEquals("alex_java_developer.pdf", dto.getFileName());
        assertTrue(dto.getResumeCharCount() > 200);
        assertTrue(dto.getResumeWordCount() > 30);
        assertTrue(dto.getResumeSnippet().contains("Alex Mercer"));
        assertTrue(dto.getMatchScore() > 0 && dto.getMatchScore() <= 100);
        assertNotNull(dto.getRating());

        assertTrue(dto.getMatchedSkills().contains("Java"));
        assertTrue(dto.getMatchedSkills().contains("Spring Boot"));
        assertTrue(dto.getMatchedSkills().contains("MySQL"));
        assertTrue(dto.getMatchedSkills().contains("REST APIs"));

        System.out.println("=== E2E SCENARIO 1 (VALID PDF) PASSED ===");
    }

    @Test
    @DisplayName("E2E Scenario 2: Successful analysis using a valid DOCX resume")
    public void testE2eScenario2ValidDocxAnalysis() {
        MockMultipartFile validDocx = new MockMultipartFile(
                "file", "david_java_fullstack.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", davidResumeDocxBytes);

        ResponseEntity<?> response = controller.analyzeResume(
                validDocx, TARGET_JAVA_JOB_DESCRIPTION, "Full Stack Engineer", "Tech Systems");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof AnalysisResponseDto);

        AnalysisResponseDto dto = (AnalysisResponseDto) response.getBody();
        assertEquals("david_java_fullstack.docx", dto.getFileName());
        assertTrue(dto.getResumeCharCount() > 200);
        assertTrue(dto.getResumeWordCount() > 30);
        assertTrue(dto.getResumeSnippet().contains("David Kim"));

        // David Kim has: Java, Spring Boot, React, MySQL, REST APIs, Git, Docker, CI/CD Pipelines
        assertTrue(dto.getMatchedSkills().contains("Java"));
        assertTrue(dto.getMatchedSkills().contains("Spring Boot"));
        assertTrue(dto.getMatchedSkills().contains("MySQL"));
        assertTrue(dto.getMatchedSkills().contains("REST APIs"));
        assertTrue(dto.getMatchedSkills().contains("Docker"));
        assertTrue(dto.getMatchedSkills().contains("CI/CD Pipelines"));
        assertTrue(dto.getMatchedSkills().contains("Git"));

        // Verify evidence snippet extraction from DOCX text
        assertNotNull(dto.getSkillEvidence().get("Java"));
        assertNotEquals("Evidence not found", dto.getSkillEvidence().get("Java"));
        assertNotNull(dto.getSkillEvidence().get("Docker"));
        assertNotEquals("Evidence not found", dto.getSkillEvidence().get("Docker"));

        System.out.println("=== E2E SCENARIO 2 (VALID DOCX) PASSED (Score: " + dto.getMatchScore() + "%) ===");
    }

    @Test
    @DisplayName("E2E Scenario 3: Rejection of invalid file types, empty files, corrupted files, and oversized resumes")
    public void testE2eScenario3InvalidEmptyCorruptOversizedResume() {
        // 1. Invalid extensions (.txt, .exe, .png, extensionless)
        MockMultipartFile txtFile = new MockMultipartFile("file", "resume.txt", "text/plain", "Java dev".getBytes());
        MockMultipartFile exeFile = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "dummy".getBytes());
        MockMultipartFile pngFile = new MockMultipartFile("file", "resume.png", "image/png", "fake-png".getBytes());
        for (MockMultipartFile file : new MockMultipartFile[]{txtFile, exeFile, pngFile}) {
            ResponseEntity<?> r = controller.analyzeResume(file, TARGET_JAVA_JOB_DESCRIPTION, null, null);
            assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        }

        // 2. Empty (0-byte) file
        MockMultipartFile emptyPdf = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        ResponseEntity<?> emptyResp = controller.analyzeResume(emptyPdf, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, emptyResp.getStatusCode());

        // 3. Corrupted PDF (non-PDF bytes with .pdf extension)
        MockMultipartFile corruptPdf = new MockMultipartFile("file", "fake.pdf", "application/pdf", new byte[]{1, 2, 3, 4, 5});
        ResponseEntity<?> corruptPdfResp = controller.analyzeResume(corruptPdf, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, corruptPdfResp.getStatusCode());

        // 4. Corrupted DOCX (non-ZIP bytes with .docx extension)
        MockMultipartFile corruptDocx = new MockMultipartFile("file", "fake.docx", "application/octet-stream", new byte[]{1, 2, 3, 4, 5});
        ResponseEntity<?> corruptDocxResp = controller.analyzeResume(corruptDocx, TARGET_JAVA_JOB_DESCRIPTION, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, corruptDocxResp.getStatusCode());

        // 5. Oversized resume (> 5MB)
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1024]; // 5MB + 1KB
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "huge.pdf", "application/pdf", oversizedBytes);
        InvalidResumeException ex = assertThrows(InvalidResumeException.class, () -> parserService.extractText(oversizedFile));
        assertTrue(ex.getMessage().contains("5MB limit"));

        System.out.println("=== E2E SCENARIO 3 (INVALID, EMPTY, CORRUPTED, OVERSIZED) PASSED ===");
    }

    @Test
    @DisplayName("E2E Scenario 4: Rejection of missing, too-short, and too-long job descriptions")
    public void testE2eScenario4MissingShortLongJobDescriptions() {
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // Missing (null)
        ResponseEntity<?> rNull = controller.analyzeResume(validPdf, null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, rNull.getStatusCode());

        // Empty ("")
        ResponseEntity<?> rEmpty = controller.analyzeResume(validPdf, "", null, null);
        assertEquals(HttpStatus.BAD_REQUEST, rEmpty.getStatusCode());

        // Whitespace only
        ResponseEntity<?> rWhitespace = controller.analyzeResume(validPdf, "   \t\n   ", null, null);
        assertEquals(HttpStatus.BAD_REQUEST, rWhitespace.getStatusCode());

        // Too short (< 15 chars)
        ResponseEntity<?> rShort = controller.analyzeResume(validPdf, "Junior Dev", null, null);
        assertEquals(HttpStatus.BAD_REQUEST, rShort.getStatusCode());

        // Too long (> 20,000 chars)
        StringBuilder hugeJd = new StringBuilder("Java Developer requirements: ");
        while (hugeJd.length() < 20_005) {
            hugeJd.append("Must have extensive experience in Spring Boot, REST APIs, and microservices architecture. ");
        }
        ResponseEntity<?> rLong = controller.analyzeResume(validPdf, hugeJd.toString(), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, rLong.getStatusCode());

        // Boundary: exactly 15 chars accepted
        ResponseEntity<?> rMinValid = controller.analyzeResume(validPdf, "Java Developer!", null, null);
        assertEquals(HttpStatus.OK, rMinValid.getStatusCode());

        System.out.println("=== E2E SCENARIO 4 (JOB DESCRIPTION VALIDATION BOUNDARIES) PASSED ===");
    }

    @Test
    @DisplayName("E2E Scenario 5: Failed server/network request followed by a successful retry")
    public void testE2eScenario5FailedRequestFollowedBySuccessfulRetry() {
        MockMultipartFile corruptFile = new MockMultipartFile(
                "file", "damaged.pdf", "application/pdf", new byte[]{0, 1, 2, 3});
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        // 1. Initial attempt fails with clear Bad Request
        ResponseEntity<?> failedResp = controller.analyzeResume(corruptFile, TARGET_JAVA_JOB_DESCRIPTION, "Dev", "Acme");
        assertEquals(HttpStatus.BAD_REQUEST, failedResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> errorBody = (Map<String, String>) failedResp.getBody();
        assertEquals("BAD_REQUEST", errorBody.get("status"));
        assertTrue(errorBody.get("message").contains("corrupted") || errorBody.get("message").contains("invalid"));

        // 2. Retry with valid resume succeeds cleanly
        ResponseEntity<?> retryResp = controller.analyzeResume(validFile, TARGET_JAVA_JOB_DESCRIPTION, "Dev", "Acme");
        assertEquals(HttpStatus.OK, retryResp.getStatusCode());
        AnalysisResponseDto successBody = (AnalysisResponseDto) retryResp.getBody();
        assertNotNull(successBody);
        assertEquals("alex_java_developer.pdf", successBody.getFileName());
        assertTrue(successBody.getMatchScore() > 0);

        System.out.println("=== E2E SCENARIO 5 (FAILURE FOLLOWED BY RETRY) PASSED ===");
    }

    @Test
    @DisplayName("E2E Scenario 6: Clear All and fresh analysis produces totally independent results")
    public void testE2eScenario6ClearAllAndFreshAnalysis() {
        MockMultipartFile javaResume = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);
        MockMultipartFile pythonResume = new MockMultipartFile(
                "file", "sarah_python_cloud.pdf", "application/pdf", pythonResumePdfBytes);

        String pythonJd = "Looking for a Python Cloud Engineer with Docker, Kubernetes, AWS, and CI/CD experience.";

        // Run 1: Java analysis
        ResponseEntity<?> javaResp = controller.analyzeResume(javaResume, TARGET_JAVA_JOB_DESCRIPTION, "Java Dev", "Acme");
        assertEquals(HttpStatus.OK, javaResp.getStatusCode());
        AnalysisResponseDto javaDto = (AnalysisResponseDto) javaResp.getBody();
        assertTrue(javaDto.getMatchedSkills().contains("Java"));

        // Simulate Clear All and fresh analysis with Python resume
        ResponseEntity<?> pythonResp = controller.analyzeResume(pythonResume, pythonJd, "DevOps Engineer", "Cloud Inc");
        assertEquals(HttpStatus.OK, pythonResp.getStatusCode());
        AnalysisResponseDto pythonDto = (AnalysisResponseDto) pythonResp.getBody();

        // Verify independent results
        assertEquals("sarah_python_cloud.pdf", pythonDto.getFileName());
        assertTrue(pythonDto.getMatchedSkills().contains("Python"));
        assertTrue(pythonDto.getMatchedSkills().contains("Docker"));
        assertTrue(pythonDto.getMatchedSkills().contains("Kubernetes"));
        assertTrue(pythonDto.getMatchedSkills().contains("AWS"));
        assertFalse(pythonDto.getMatchedSkills().contains("Java"), "Python analysis must not inherit Java from previous run");

        System.out.println("=== E2E SCENARIO 6 (FRESH ANALYSIS INDEPENDENCE) PASSED ===");
    }

    @Test
    @DisplayName("E2E Scenario 7: Verify skill evidence, required/preferred classification, and experience scoring")
    public void testE2eScenario7SkillEvidenceClassificationAndScoring() {
        MockMultipartFile javaResume = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);

        String structuredJd = "We are seeking a Backend Java Developer with 2+ years of experience.\n" +
                "Requirements:\n" +
                "- Java, Spring Boot, MySQL, REST APIs, Git\n" +
                "Preferred Qualifications:\n" +
                "- Docker, Kubernetes, AWS";

        ResponseEntity<?> response = controller.analyzeResume(javaResume, structuredJd, "Backend Dev", "Tech Co");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        AnalysisResponseDto dto = (AnalysisResponseDto) response.getBody();

        // 1. Skill Evidence verification
        assertNotNull(dto.getSkillEvidence());
        for (String skill : dto.getMatchedSkills()) {
            String evidence = dto.getSkillEvidence().get(skill);
            assertNotNull(evidence, "Every matched skill must have an evidence entry");
            assertNotEquals("Evidence not found", evidence, "Matched skill '" + skill + "' must contain real resume evidence");
        }

        // 2. Required vs Preferred classification
        assertTrue(dto.isSkillsClassified());
        assertEquals(5, dto.getRequiredMatchedSkills().size());
        assertTrue(dto.getRequiredMatchedSkills().contains("Java"));
        assertTrue(dto.getRequiredMatchedSkills().contains("Spring Boot"));
        assertTrue(dto.getRequiredMatchedSkills().contains("MySQL"));
        assertTrue(dto.getMissingRequiredSkills().isEmpty());

        assertEquals(3, dto.getMissingPreferredSkills().size());
        assertTrue(dto.getMissingPreferredSkills().contains("Docker"));
        assertTrue(dto.getMissingPreferredSkills().contains("Kubernetes"));
        assertTrue(dto.getMissingPreferredSkills().contains("AWS"));

        // 3. Experience alignment scoring
        assertTrue(dto.getScoreBreakdown().get("Experience Alignment") >= 85,
                "Candidate with 2 years meeting 2+ years requirement should score >= 85");

        System.out.println("=== E2E SCENARIO 7 (EVIDENCE, CLASSIFICATION, SCORING) PASSED ===");
    }

    @Test
    @DisplayName("E2E Scenario 8: Backend and controller flow verification")
    public void testE2eScenario8BackendEndpointsAndContractIntegrity() {
        // Verify controller handles missing parameters gracefully
        ResponseEntity<?> nullParamResp = controller.analyzeResume(null, null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, nullParamResp.getStatusCode());

        // Verify valid multipart flow
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "alex_java_developer.pdf", "application/pdf", javaResumePdfBytes);
        ResponseEntity<?> okResp = controller.analyzeResume(validPdf, TARGET_JAVA_JOB_DESCRIPTION, "Dev", "Co");
        assertEquals(HttpStatus.OK, okResp.getStatusCode());

        System.out.println("=== E2E SCENARIO 8 (BACKEND FLOW INTEGRITY) PASSED ===");
    }
}





