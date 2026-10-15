package com.resume.analyzer.service;

import com.resume.analyzer.dto.AnalysisResponseDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core business service for analyzing resumes against job descriptions.
 * Dynamically parses skills, computes match scores, detects missing competencies,
 * and generates targeted improvement recommendations.
 */
@Service
public class AnalysisService {

    // Predefined catalog of recognized technical & professional skills
    // Format: Canonical Name -> Regex patterns to match safely
    private static final Map<String, List<Pattern>> SKILL_DICTIONARY = new LinkedHashMap<>();

    static {
        registerSkill("Java", "\\bjava\\b(?!\\s*script)");
        registerSkill("Python", "\\bpython\\b");
        // 'js' alias kept but only bare 'js' — still low false-positive risk in prose
        registerSkill("JavaScript", "\\b(javascript|js)\\b");
        registerSkill("TypeScript", "\\b(typescript|ts)\\b");
        registerSkill("C++", "\\bc\\+\\+\\b");
        registerSkill("C#", "\\bc#\\b|\\bcsharp\\b");
        registerSkill("Go", "\\bgolang\\b|\\bgo\\s+(language|programming|developer|backend)\\b");
        registerSkill("Rust", "\\brust\\b(?!\\s*iron)");
        registerSkill("PHP", "\\bphp\\b");
        registerSkill("Ruby", "\\bruby\\b");
        registerSkill("Kotlin", "\\bkotlin\\b");
        registerSkill("Swift", "\\bswift\\b");
        registerSkill("SQL", "\\bsql\\b");
        registerSkill("HTML5 & CSS3", "\\b(html|html5|css|css3)\\b");

        // Frameworks & Libraries
        registerSkill("Spring Boot", "\\bspring\\s*boot\\b");
        registerSkill("Spring Data JPA", "\\b(spring\\s*data(\\s*jpa)?|jpa|hibernate)\\b");

        // FIX: React — require .js suffix, 'reactjs', or an adjacent tech context word
        // Prevents false positive: "react to feedback", "react well under pressure"
        registerSkill("React", "\\breact\\.?js\\b|\\breactjs\\b|" +
                "\\breact\\s+(framework|component[s]?|hook[s]?|library|app|application|developer|development|frontend|ui)\\b|" +
                "\\b(framework|component[s]?|hook[s]?|library)\\s+react\\b");

        registerSkill("Angular", "\\bangular(\\.?js)?\\b");
        registerSkill("Vue.js", "\\bvue(\\.?js)?\\b");

        // FIX: Node.js — require explicit .js or 'nodejs' to avoid matching generic 'node'
        // Prevents false positive: "network node", "tree node traversal"
        registerSkill("Node.js", "\\bnode\\.js\\b|\\bnodejs\\b|\\bnode\\.?js\\s+(developer|backend|server|runtime|environment)\\b");

        // FIX: Express.js — require .js or 'expressjs' to avoid matching English verb 'express'
        // Prevents false positive: "express your creativity", "express delivery"
        registerSkill("Express.js", "\\bexpress\\.js\\b|\\bexpressjs\\b|\\bexpress\\s+(framework|middleware|router|route[s]?|server|js)\\b");

        registerSkill("Django", "\\bdjango\\b");
        registerSkill("Flask", "\\bflask\\b");
        registerSkill("FastAPI", "\\bfastapi\\b");

        // FIX: Next.js — require .js or 'nextjs' to avoid matching "next step", "next release"
        // Prevents false positive: "the next release", "next in line"
        registerSkill("Next.js", "\\bnext\\.js\\b|\\bnextjs\\b|\\bnext\\.?js\\s+(developer|framework|app|application|project)\\b");

        registerSkill("ASP.NET", "\\basp\\.?net\\b");
        registerSkill("Bootstrap", "\\bbootstrap\\b");
        registerSkill("Tailwind CSS", "\\btailwind(\\.?css)?\\b");

        // Databases
        registerSkill("MySQL", "\\bmysql\\b");
        registerSkill("PostgreSQL", "\\b(postgresql|postgres)\\b");
        registerSkill("MongoDB", "\\bmongodb\\b|\\bmongo\\b");
        registerSkill("Redis", "\\bredis\\b");
        registerSkill("Oracle Database", "\\boracle(\\s*db|\\s*database)?\\b");
        registerSkill("SQLite", "\\bsqlite\\b");
        registerSkill("Elasticsearch", "\\belasticsearch\\b");

        // Cloud & DevOps
        registerSkill("Docker", "\\bdocker\\b");
        registerSkill("Kubernetes", "\\b(kubernetes|k8s)\\b");
        registerSkill("AWS", "\\b(aws|amazon\\s*web\\s*services|ec2|s3|lambda)\\b");
        registerSkill("Azure", "\\b(azure|microsoft\\s*azure)\\b");
        registerSkill("Google Cloud (GCP)", "\\b(gcp|google\\s*cloud(\\s*platform)?)\\b");

        // FIX: CI/CD — adds 'continuous delivery' and 'continuous deployment' variants
        // Also adds 'pipeline[s]' as a strong DevOps signal
        // Preserves existing ci/cd and continuous integration matches
        registerSkill("CI/CD Pipelines",
                "\\bci\\s*/\\s*cd\\b|" +                         // CI/CD
                "\\bcicd\\b|" +                                   // CICD (no slash)
                "\\bcontinuous\\s+integration\\b|" +              // continuous integration
                "\\bcontinuous\\s+delivery\\b|" +                 // continuous delivery (NEW)
                "\\bcontinuous\\s+deployment\\b|" +               // continuous deployment (NEW)
                "\\b(build|deployment|release|ci)\\s+pipeline[s]?\\b"); // pipeline context (NEW)

        registerSkill("Jenkins", "\\bjenkins\\b");
        registerSkill("GitHub Actions", "\\bgithub\\s*actions\\b");
        registerSkill("Terraform", "\\bterraform\\b");
        registerSkill("Linux", "\\blinux\\b|\\bubuntu\\b");

        // Architecture & Methodologies

        // FIX: REST APIs — 'rest' alone matched plain English (e.g. "I need a rest", "the rest of the team")
        // Now requires 'restful', 'rest api(s)', 'rest endpoint(s)', 'rest service(s)', or 'web api(s)'
        registerSkill("REST APIs",
                "\\brestful\\b|" +                                    // restful (adjective in tech context)
                "\\brest\\s+api[s]?\\b|" +                           // REST API / REST APIs
                "\\brest\\s+endpoint[s]?\\b|" +                      // REST endpoints
                "\\brest\\s+service[s]?\\b|" +                       // REST services
                "\\brest\\s+web\\s*service[s]?\\b|" +                // REST web services
                "\\bweb\\s*api[s]?\\b");                              // Web APIs

        registerSkill("GraphQL", "\\bgraphql\\b");
        registerSkill("Microservices", "\\bmicroservices?\\b");
        registerSkill("Kafka", "\\bkafka\\b");
        registerSkill("RabbitMQ", "\\brabbitmq\\b");

        // FIX: Unit Testing — removed bare 'testing' which matched any sentence mentioning testing
        // Keeps junit, mockito, unit test(s), integration test(s), test automation
        registerSkill("Unit Testing (JUnit/Mockito)",
                "\\bjunit\\b|" +
                "\\bmockito\\b|" +
                "\\bunit\\s*tests?\\b|" +                            // unit test / unit tests
                "\\bintegration\\s*tests?\\b|" +                     // integration test(s)
                "\\btest\\s+automation\\b|" +                        // test automation
                "\\btdd\\b");                                         // Test-Driven Development

        registerSkill("Agile / Scrum", "\\b(agile|scrum|kanban)\\b");
        registerSkill("Object-Oriented Programming (OOP)", "\\b(oop|object\\s*oriented)\\b");

        // Tools
        registerSkill("Git", "\\bgit\\b|\\bgithub\\.?com\\b|\\bgithub\\b|\\bgitlab\\b");
        registerSkill("Maven", "\\bmaven\\b");
        registerSkill("Gradle", "\\bgradle\\b");
        registerSkill("Postman", "\\bpostman\\b");
        registerSkill("Docker Compose", "\\bdocker\\s*compose\\b");
    }

    private static void registerSkill(String canonicalName, String regex) {
        SKILL_DICTIONARY.computeIfAbsent(canonicalName, k -> new ArrayList<>())
                .add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    /**
     * Performs end-to-end dynamic analysis of an extracted resume against a job description.
     */
    public AnalysisResponseDto analyze(String fileName, String resumeText, String jobDescription) {
        AnalysisResponseDto dto = new AnalysisResponseDto();
        dto.setFileName(fileName);
        dto.setResumeCharCount(resumeText.length());

        String[] words = resumeText.trim().split("\\s+");
        dto.setResumeWordCount(words.length);

        // Keep a short snippet (first 180 chars) for UI proof of extraction
        String snippet = resumeText.length() > 180 ? resumeText.substring(0, 180) + "..." : resumeText;
        dto.setResumeSnippet(snippet.replaceAll("\\s+", " "));

        // 1. Dynamic Skill Extraction
        Set<String> resumeSkills = extractSkills(resumeText);

        // Classify job description skills into Required vs Preferred buckets
        JobSkillClassification classification = classifyJobSkills(jobDescription);
        boolean classified = classification.isClassified();

        // Combined set of all JD skills — used for backward-compatible scoring
        Set<String> jobSkills = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        jobSkills.addAll(classification.requiredSkills());
        jobSkills.addAll(classification.preferredSkills());

        // If job description matched no catalog skills at all, fallback to key terms
        if (jobSkills.isEmpty()) {
            jobSkills = extractFallbackJobKeywords(jobDescription);
            classified = false; // fallback keywords have no classification
        }

        // 2. Compute Sets: Matched, Missing, and Additional (unchanged from existing logic)
        Set<String> matched = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> missing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> additional = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (String skill : jobSkills) {
            if (resumeSkills.contains(skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        for (String skill : resumeSkills) {
            if (!jobSkills.contains(skill)) {
                additional.add(skill);
            }
        }

        dto.setMatchedSkills(new ArrayList<>(matched));
        dto.setMissingSkills(new ArrayList<>(missing));
        dto.setAdditionalSkills(new ArrayList<>(additional));

        // Evidence snippets — sourced exclusively from the in-memory resume text
        dto.setSkillEvidence(extractEvidenceSnippets(resumeText, matched));

        // Phase 4 Step 2: Classified required/preferred breakdown
        dto.setSkillsClassified(classified);
        if (classified) {
            Set<String> reqMatched  = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> reqMissing  = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> prefMatched = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> prefMissing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            for (String skill : classification.requiredSkills()) {
                if (resumeSkills.contains(skill)) reqMatched.add(skill);
                else                              reqMissing.add(skill);
            }
            for (String skill : classification.preferredSkills()) {
                if (resumeSkills.contains(skill)) prefMatched.add(skill);
                else                              prefMissing.add(skill);
            }

            dto.setRequiredMatchedSkills(new ArrayList<>(reqMatched));
            dto.setMissingRequiredSkills(new ArrayList<>(reqMissing));
            dto.setPreferredMatchedSkills(new ArrayList<>(prefMatched));
            dto.setMissingPreferredSkills(new ArrayList<>(prefMissing));
        }

        // 3. Compute Scores
        int skillsMatchScore = jobSkills.isEmpty() ? 70 : (int) Math.round(((double) matched.size() / jobSkills.size()) * 100);
        int experienceScore = calculateExperienceAlignment(resumeText, jobDescription);
        int educationScore = calculateEducationAlignment(resumeText);
        int keywordScore = calculateKeywordAlignment(resumeText, jobDescription);

        // Weighted Overall Score: 50% Skills + 20% Keywords + 15% Experience + 15% Education
        int overallScore = (int) Math.round(
                (skillsMatchScore * 0.50) +
                (keywordScore * 0.20) +
                (experienceScore * 0.15) +
                (educationScore * 0.15)
        );

        overallScore = Math.max(15, Math.min(98, overallScore));
        dto.setMatchScore(overallScore);

        // Rating Label
        if (overallScore >= 80) {
            dto.setRating("Strong Match");
        } else if (overallScore >= 65) {
            dto.setRating("Good Match");
        } else if (overallScore >= 50) {
            dto.setRating("Moderate Match");
        } else {
            dto.setRating("Needs Optimization");
        }

        // Score Breakdown
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("Required Skills Match", Math.min(100, skillsMatchScore));
        breakdown.put("Experience Alignment", Math.min(100, experienceScore));
        breakdown.put("Education & Qualifications", Math.min(100, educationScore));
        breakdown.put("Job Keyword Alignment", Math.min(100, keywordScore));
        dto.setScoreBreakdown(breakdown);

        // 4. Targeted Recommendations
        dto.setRecommendations(generateRecommendations(missing, additional, experienceScore, educationScore));

        return dto;
    }

    // ── Inner record: result of classifying JD skills into required vs preferred ──
    /**
     * Holds two disjoint sets of canonical skill names extracted from a job description.
     * {@code classified} is true only when explicit signals (headings or inline phrases)
     * were found in the JD; false for ambiguous descriptions.
     */
    public static final class JobSkillClassification {
        private final Set<String> requiredSkills;
        private final Set<String> preferredSkills;
        private final boolean classified;

        public JobSkillClassification(Set<String> required, Set<String> preferred, boolean classified) {
            this.requiredSkills  = Collections.unmodifiableSet(required);
            this.preferredSkills = Collections.unmodifiableSet(preferred);
            this.classified      = classified;
        }
        public Set<String> requiredSkills()  { return requiredSkills; }
        public Set<String> preferredSkills() { return preferredSkills; }
        public boolean isClassified()        { return classified; }
    }

    // Section headings that signal a "required" block in the JD (case-insensitive)
    private static final Pattern REQUIRED_HEADING = Pattern.compile(
            "^\\s*(?:requirements?|required\\s+(?:skills?|qualifications?)|must(?:\\s+have)?|" +
            "minimum\\s+qualifications?|basic\\s+qualifications?|core\\s+(?:skills?|requirements?))" +
            "\\s*[:\\-]?\\s*$",
            Pattern.CASE_INSENSITIVE);

    // Section headings that signal a "preferred" block in the JD (case-insensitive)
    private static final Pattern PREFERRED_HEADING = Pattern.compile(
            "^\\s*(?:preferred(?:\\s+(?:skills?|qualifications?))?|nice\\s+to\\s+have|" +
            "bonus(?:\\s+(?:skills?|qualifications?))?|desired(?:\\s+(?:skills?|qualifications?))?|" +
            "additional\\s+(?:skills?|qualifications?)|advantageous)\\s*[:\\-]?\\s*$",
            Pattern.CASE_INSENSITIVE);

    // Inline phrases that mark a sentence/bullet as preferred (e.g. "Docker is a strong plus")
    private static final Pattern PREFERRED_INLINE = Pattern.compile(
            "(?:strong\\s+plus|nice\\s+to\\s+have|is\\s+a\\s+plus|bonus|preferred|advantageous|" +
            "knowledge\\s+of|familiarity\\s+with|exposure\\s+to)",
            Pattern.CASE_INSENSITIVE);

    // Inline phrases that mark a sentence/bullet as required
    private static final Pattern REQUIRED_INLINE = Pattern.compile(
            "(?:must\\s+have|required|mandatory|essential|minimum|core\\s+requirement)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Classifies skills found in the job description into Required and Preferred buckets.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Split the JD into lines and detect section-heading transitions between
     *       Required and Preferred blocks.</li>
     *   <li>For lines that belong to no explicit section, use inline trigger phrases
     *       ("strong plus", "must have", etc.) to classify individual bullets.</li>
     *   <li>If the JD is entirely ambiguous (no signals found), put all skills in
     *       Required and set {@code classified=false}, preserving backward compatibility.</li>
     * </ol>
     *
     * @param jobDescription raw job description text
     * @return a {@link JobSkillClassification} with the split skill sets
     */
    public JobSkillClassification classifyJobSkills(String jobDescription) {
        Set<String> required  = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> preferred = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (jobDescription == null || jobDescription.isBlank()) {
            return new JobSkillClassification(required, preferred, false);
        }

        String[] lines = jobDescription.split("\\r?\\n");
        boolean anyHeadingFound = false;

        // "neutral" = no active section heading; we classify neutrals by inline phrases later
        enum Section { NEUTRAL, REQUIRED, PREFERRED }
        Section currentSection = Section.NEUTRAL;

        // Map each line to a section tag and the skills found in it
        // We process as a two-pass: first collect by section, then reclassify neutral lines
        List<String[]> lineData = new ArrayList<>(); // [sectionTag, lineText]

        for (String line : lines) {
            String trimmed = line.trim();

            if (REQUIRED_HEADING.matcher(trimmed).matches()) {
                currentSection = Section.REQUIRED;
                anyHeadingFound = true;
                continue; // heading row itself has no skills
            }
            if (PREFERRED_HEADING.matcher(trimmed).matches()) {
                currentSection = Section.PREFERRED;
                anyHeadingFound = true;
                continue;
            }

            lineData.add(new String[]{currentSection.name(), line});
        }

        // Collect skills for each line
        for (String[] entry : lineData) {
            Section sec = Section.valueOf(entry[0]);
            String  txt = entry[1];
            Set<String> lineSkills = extractSkills(txt);
            if (lineSkills.isEmpty()) continue;

            if (sec == Section.PREFERRED) {
                preferred.addAll(lineSkills);
            } else if (sec == Section.REQUIRED) {
                required.addAll(lineSkills);
            } else {
                // NEUTRAL line — classify by inline phrase
                if (PREFERRED_INLINE.matcher(txt).find() && !REQUIRED_INLINE.matcher(txt).find()) {
                    preferred.addAll(lineSkills);
                    anyHeadingFound = true; // inline signal counts as a classification signal
                } else if (REQUIRED_INLINE.matcher(txt).find()) {
                    required.addAll(lineSkills);
                    anyHeadingFound = true;
                } else {
                    // Truly ambiguous line — put in required (safe default)
                    required.addAll(lineSkills);
                }
            }
        }

        // Ensure a skill cannot be in both sets — preferred takes precedence when detected
        required.removeAll(preferred);

        return new JobSkillClassification(required, preferred, anyHeadingFound);
    }

    /**
     * Extracts recognized skills from text based on regex patterns.
     */
    public Set<String> extractSkills(String text) {
        Set<String> detected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (text == null || text.isBlank()) {
            return detected;
        }

        for (Map.Entry<String, List<Pattern>> entry : SKILL_DICTIONARY.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    detected.add(entry.getKey());
                    break;
                }
            }
        }
        return detected;
    }

    /**
     * For each matched skill, locates a short surrounding context snippet from the
     * actual in-memory resume text.  Evidence is NEVER taken from the job description
     * and is NEVER invented — if no regex pattern fires on the resume, the value is
     * "Evidence not found".
     *
     * @param resumeText    the extracted resume text (in-memory only, never persisted)
     * @param matchedSkills canonical skill names confirmed to exist in the resume
     * @return ordered map: canonical skill name -> evidence snippet
     */
    public Map<String, String> extractEvidenceSnippets(String resumeText, Collection<String> matchedSkills) {
        Map<String, String> evidence = new LinkedHashMap<>();
        if (resumeText == null || resumeText.isBlank() || matchedSkills == null || matchedSkills.isEmpty()) {
            return evidence;
        }

        // Collapse newlines/tabs to spaces so snippets read as clean prose
        String normalizedResume = resumeText.replaceAll("\\s+", " ").trim();

        for (String skill : matchedSkills) {
            List<Pattern> patterns = SKILL_DICTIONARY.get(skill);
            if (patterns == null) {
                evidence.put(skill, "Evidence not found");
                continue;
            }

            String snippet = null;
            for (Pattern pattern : patterns) {
                Matcher m = pattern.matcher(normalizedResume);
                if (m.find()) {
                    int matchStart = m.start();
                    int matchEnd   = m.end();

                    // Grab up to 60 chars of context on each side
                    int ctxStart = Math.max(0, matchStart - 60);
                    int ctxEnd   = Math.min(normalizedResume.length(), matchEnd + 60);

                    StringBuilder sb = new StringBuilder();
                    if (ctxStart > 0)                         sb.append("...");
                    sb.append(normalizedResume, ctxStart, ctxEnd);
                    if (ctxEnd < normalizedResume.length())   sb.append("...");

                    snippet = sb.toString().trim();
                    break; // first matching pattern wins
                }
            }

            evidence.put(skill, snippet != null ? snippet : "Evidence not found");
        }

        return evidence;
    }

    private Set<String> extractFallbackJobKeywords(String jobDescription) {
        Set<String> keywords = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Pattern wordPattern = Pattern.compile("\\b[A-Z][a-zA-Z0-9#+]{2,15}\\b");
        Matcher matcher = wordPattern.matcher(jobDescription);
        while (matcher.find()) {
            String word = matcher.group();
            if (!List.of("The", "And", "With", "For", "You", "Our", "Key", "Job", "Role", "Team", "Work").contains(word)) {
                keywords.add(word);
            }
            if (keywords.size() >= 8) break;
        }
        return keywords;
    }

    // ── Experience Requirement & Candidate Extraction Models ──
    public static final class ExperienceRequirement {
        private final double minYears;
        private final double maxYears;
        private final boolean specified;
        private final String rawMatch;

        public ExperienceRequirement(double minYears, double maxYears, boolean specified, String rawMatch) {
            this.minYears = minYears;
            this.maxYears = maxYears;
            this.specified = specified;
            this.rawMatch = rawMatch;
        }

        public double getMinYears() { return minYears; }
        public double getMaxYears() { return maxYears; }
        public boolean isSpecified() { return specified; }
        public String getRawMatch() { return rawMatch; }
    }

    public static final class CandidateExperience {
        private final double years;
        private final boolean hasExplicitYears;
        private final boolean relevant;
        private final boolean hasProjectEvidence;
        private final String snippet;

        public CandidateExperience(double years, boolean hasExplicitYears, boolean relevant, boolean hasProjectEvidence, String snippet) {
            this.years = years;
            this.hasExplicitYears = hasExplicitYears;
            this.relevant = relevant;
            this.hasProjectEvidence = hasProjectEvidence;
            this.snippet = snippet;
        }

        public double getYears() { return years; }
        public boolean hasExplicitYears() { return hasExplicitYears; }
        public boolean isRelevant() { return relevant; }
        public boolean hasProjectEvidence() { return hasProjectEvidence; }
        public String getSnippet() { return snippet; }
    }

    private static final Pattern ENTRY_LEVEL_PATTERN = Pattern.compile(
            "\\b(entry[\\s-]level|fresher|intern(?:ship)?|no\\s+(?:prior\\s+)?experience\\s+required|0\\s*[-–—to]\\s*1\\s*(?:years?|yrs?)|0\\+?\\s*(?:years?|yrs?))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JD_RANGE_PATTERN = Pattern.compile(
            "(?:(?:minimum|min\\.?|at least|requires?|seeking|with)\\s+)?(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\s*(?:-|–|—|\\bto\\b)\\s*(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\+?\\s*(?:years?|yrs?)(?:\\s+(?:of|in))?(?:\\s+[\\w\\s]{0,30})?\\s*experience\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JD_MIN_PLUS_PATTERN = Pattern.compile(
            "(?:\\b(?:minimum|min\\.?|at least|requires?|must have)\\s+(?:of\\s+)?)?(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\s*\\+\\s*(?:years?|yrs?)(?:\\s+(?:of|in))?(?:\\s+[\\w\\s]{0,30})?\\s*experience\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JD_MIN_PATTERN = Pattern.compile(
            "\\b(?:minimum|min\\.?|at least|requires?|must have)\\s+(?:of\\s+)?(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\s*(?:years?|yrs?)(?:\\s+(?:of|in))?(?:\\s+[\\w\\s]{0,30})?\\s*experience\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JD_PLAIN_EXP_PATTERN = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\s*(?:years?|yrs?)(?:\\s+(?:of|in))?(?:\\s+[\\w\\s]{0,30})?\\s*experience\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RESUME_EXP_YEARS_PATTERN = Pattern.compile(
            "\\b(?:over|more than|approx(?:imately)?|around)?\\s*(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\+?\\s*(?:years?|yrs?)(?:\\s+of)?(?:\\s+[\\w\\s]{0,30})?\\s*experience\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RESUME_SPECIALIZING_PATTERN = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\+?\\s*(?:years?|yrs?)\\s+(?:specializing|working|as|in)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RESUME_DATE_RANGE_PATTERN = Pattern.compile(
            "\\b(20\\d{2})\\s*(?:-|–|—|\\bto\\b)\\s*(20\\d{2}|present|current|now)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TECH_CONTEXT_PATTERN = Pattern.compile(
            "\\b(software|developer|engineer|engineering|programmer|programming|backend|frontend|" +
            "full\\s*stack|web|cloud|devops|data|qa|architect|systems?|technology|coding|" +
            "computer\\s+science|infrastructure|api|microservices?|java|python|javascript|typescript|c\\+\\+|c#|sql)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ACTION_VERB_PATTERN = Pattern.compile(
            "\\b(developed|engineered|designed|architected|built|implemented|created|deployed|configured|maintained|led)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROJECT_NOUN_PATTERN = Pattern.compile(
            "\\b(project[s]?|responsibilities|deliverables|repository|application[s]?|microservices?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static double parseNumber(String numStr) {
        if (numStr == null) return 0.0;
        String clean = numStr.trim().toLowerCase();
        switch (clean) {
            case "one": return 1.0;
            case "two": return 2.0;
            case "three": return 3.0;
            case "four": return 4.0;
            case "five": return 5.0;
            case "six": return 6.0;
            case "seven": return 7.0;
            case "eight": return 8.0;
            case "nine": return 9.0;
            case "ten": return 10.0;
            default:
                try {
                    return Double.parseDouble(clean);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
        }
    }

    public ExperienceRequirement extractRequiredExperience(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return new ExperienceRequirement(0.0, 0.0, false, null);
        }

        Matcher m = ENTRY_LEVEL_PATTERN.matcher(jobDescription);
        if (m.find()) {
            return new ExperienceRequirement(0.0, 1.0, true, m.group());
        }

        m = JD_RANGE_PATTERN.matcher(jobDescription);
        if (m.find()) {
            double min = parseNumber(m.group(1));
            double max = parseNumber(m.group(2));
            return new ExperienceRequirement(min, max, true, m.group());
        }

        m = JD_MIN_PLUS_PATTERN.matcher(jobDescription);
        if (m.find()) {
            double min = parseNumber(m.group(1));
            return new ExperienceRequirement(min, min, true, m.group());
        }

        m = JD_MIN_PATTERN.matcher(jobDescription);
        if (m.find()) {
            double min = parseNumber(m.group(1));
            return new ExperienceRequirement(min, min, true, m.group());
        }

        m = JD_PLAIN_EXP_PATTERN.matcher(jobDescription);
        if (m.find()) {
            double min = parseNumber(m.group(1));
            return new ExperienceRequirement(min, min, true, m.group());
        }

        return new ExperienceRequirement(0.0, 0.0, false, null);
    }

    public CandidateExperience extractCandidateExperience(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return new CandidateExperience(0.0, false, false, false, null);
        }

        double years = 0.0;
        boolean hasExplicit = false;
        String snippet = null;

        Matcher m = RESUME_EXP_YEARS_PATTERN.matcher(resumeText);
        if (m.find()) {
            years = parseNumber(m.group(1));
            hasExplicit = true;
            snippet = m.group();
        } else {
            m = RESUME_SPECIALIZING_PATTERN.matcher(resumeText);
            if (m.find()) {
                years = parseNumber(m.group(1));
                hasExplicit = true;
                snippet = m.group();
            } else {
                // Fallback: check for date ranges if no explicit summary years
                Matcher dateMatcher = RESUME_DATE_RANGE_PATTERN.matcher(resumeText);
                if (dateMatcher.find()) {
                    try {
                        int start = Integer.parseInt(dateMatcher.group(1));
                        String endStr = dateMatcher.group(2).toLowerCase();
                        int end = endStr.matches("\\d+") ? Integer.parseInt(endStr) : 2026;
                        int diff = Math.max(0, end - start);
                        if (diff > 0 && diff <= 30) {
                            years = diff;
                            hasExplicit = true;
                            snippet = dateMatcher.group();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Check relevance: context around the snippet or the overall resume
        boolean isRelevant = false;
        if (hasExplicit && snippet != null) {
            int startIdx = resumeText.indexOf(snippet);
            if (startIdx >= 0) {
                int ctxStart = Math.max(0, startIdx - 100);
                int ctxEnd = Math.min(resumeText.length(), startIdx + snippet.length() + 100);
                String context = resumeText.substring(ctxStart, ctxEnd);
                isRelevant = TECH_CONTEXT_PATTERN.matcher(context).find();
            } else {
                isRelevant = TECH_CONTEXT_PATTERN.matcher(resumeText).find();
            }
        } else if (hasExplicit) {
            isRelevant = TECH_CONTEXT_PATTERN.matcher(resumeText).find();
        }

        boolean hasProject = ACTION_VERB_PATTERN.matcher(resumeText).find() && PROJECT_NOUN_PATTERN.matcher(resumeText).find();

        return new CandidateExperience(years, hasExplicit, isRelevant, hasProject, snippet);
    }

    public int calculateExperienceAlignment(String resume, String jobDesc) {
        ExperienceRequirement req = extractRequiredExperience(jobDesc);
        CandidateExperience cand = extractCandidateExperience(resume);

        double reqYears = req.getMinYears();
        double candYears = cand.isRelevant() ? cand.getYears() : 0.0;

        int score;

        if (req.isSpecified()) {
            if (reqYears == 0.0) {
                // Entry-level or internship requirement
                if (candYears >= 2.0) {
                    score = 95;
                } else if (candYears >= 0.5 || cand.hasProjectEvidence()) {
                    score = 90;
                } else {
                    score = 75;
                }
            } else {
                if (candYears > reqYears) {
                    // Exceeds requirement
                    score = Math.min(100, 90 + (int) Math.round((candYears - reqYears) * 4));
                } else if (candYears >= reqYears - 0.25) {
                    // Meets requirement
                    score = cand.hasProjectEvidence() ? 90 : 88;
                } else if (candYears > 0) {
                    // Proportionally scales based on ratio of required experience met
                    // e.g. 75% ratio yields ~79, 50% ratio yields ~69, 25% ratio yields ~60
                    double ratio = candYears / reqYears;
                    score = (int) Math.round(50 + ratio * 38);
                } else {
                    // No relevant years found
                    if (cand.hasExplicitYears() && !cand.isRelevant()) {
                        score = 40; // Unrelated work history
                    } else if (cand.hasProjectEvidence()) {
                        score = 50; // Hands-on project capability present, but missing professional tenure
                    } else {
                        score = 35; // Completely missing required experience
                    }
                }
            }
        } else {
            // JD does NOT specify explicit years — neutral / fair baseline
            if (candYears >= 3.0) {
                score = 90;
            } else if (candYears >= 1.0) {
                score = 85;
            } else if (cand.hasProjectEvidence()) {
                score = 75; // Neutral baseline for project evidence
            } else if (cand.hasExplicitYears() && !cand.isRelevant()) {
                score = 65; // Unrelated experience
            } else {
                score = 60; // No experience, no project evidence
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    private int calculateEducationAlignment(String resume) {
        String lowerResume = resume.toLowerCase();
        if (lowerResume.contains("bachelor") || lowerResume.contains("master") || lowerResume.contains("b.tech")
                || lowerResume.contains("computer science") || lowerResume.contains("b.e.") || lowerResume.contains("degree")) {
            return 90;
        }
        if (lowerResume.contains("diploma") || lowerResume.contains("certified") || lowerResume.contains("certification")) {
            return 75;
        }
        return 60;
    }

    private int calculateKeywordAlignment(String resume, String jobDesc) {
        Set<String> jobWords = tokenize(jobDesc);
        Set<String> resumeWords = tokenize(resume);

        if (jobWords.isEmpty() || resumeWords.isEmpty()) return 50;

        int matches = 0;
        for (String jw : jobWords) {
            if (resumeWords.contains(jw)) {
                matches++;
            }
        }

        double ratio = (double) matches / jobWords.size();
        return (int) Math.min(100, Math.round(ratio * 120));
    }

    private Set<String> tokenize(String text) {
        String[] raw = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        Set<String> set = new HashSet<>();
        for (String w : raw) {
            if (w.length() >= 4 && !STOPWORDS.contains(w)) {
                set.add(w);
            }
        }
        return set;
    }

    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "have", "will", "your", "team", "work", "role", "must", "good",
            "able", "help", "plus", "more", "into", "also", "some", "such", "well", "high", "over", "only"
    );

    private List<String> generateRecommendations(Set<String> missing, Set<String> additional, int expScore, int eduScore) {
        List<String> list = new ArrayList<>();

        if (!missing.isEmpty()) {
            List<String> topMissing = missing.stream().limit(3).collect(Collectors.toList());
            list.add("Targeted Skills Gap: The job description explicitly seeks experience in " +
                    String.join(", ", topMissing) + ". If you have used these in personal projects or coursework, make sure they appear prominently.");
        }

        if (expScore < 85) {
            list.add("Action-Oriented Bullet Points: Quantify your project achievements using metrics (e.g. 'Engineered REST endpoints reducing query latency by 30%').");
        }

        if (!additional.isEmpty()) {
            List<String> topExtra = additional.stream().limit(3).collect(Collectors.toList());
            list.add("Value-Add Competencies: You possess transferable skills (" + String.join(", ", topExtra) +
                    ") that were not explicitly requested. Frame these as bonus strengths in your summary.");
        }

        if (list.isEmpty()) {
            list.add("Strong alignment detected! Ensure your resume layout is clean, ATS-compliant, and free of complex tables or graphics.");
        }

        return list;
    }
}
