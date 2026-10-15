package com.resume.analyzer.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing the dynamic analysis results returned to the frontend.
 */
public class AnalysisResponseDto {

    private String fileName;
    private int resumeCharCount;
    private int resumeWordCount;
    private String resumeSnippet;

    private int matchScore;
    private String rating;

    private Map<String, Integer> scoreBreakdown;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> additionalSkills;
    private List<String> recommendations;

    /**
     * Per-skill evidence snippets extracted from the actual uploaded resume text.
     * Key = canonical skill name (e.g. "Java"), Value = short surrounding context
     * from the resume, or "Evidence not found" if no reliable snippet could be located.
     * This map is populated only from in-memory resume text — never from disk or the database.
     */
    private Map<String, String> skillEvidence;

    // ── Phase 4 Step 2: Required vs Preferred skill classification ──────────
    /**
     * Whether the job description contained explicit Required/Preferred sections.
     * False when the JD is ambiguous and no classification was possible.
     */
    private boolean skillsClassified;

    /** Required skills from the JD that were found in the resume. */
    private List<String> requiredMatchedSkills;
    /** Required skills from the JD that were NOT found in the resume. */
    private List<String> missingRequiredSkills;
    /** Preferred/nice-to-have skills from the JD that were found in the resume. */
    private List<String> preferredMatchedSkills;
    /** Preferred/nice-to-have skills from the JD that were NOT found in the resume. */
    private List<String> missingPreferredSkills;

    private LocalDateTime analyzedAt;

    public AnalysisResponseDto() {
        this.analyzedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getResumeCharCount() { return resumeCharCount; }
    public void setResumeCharCount(int resumeCharCount) { this.resumeCharCount = resumeCharCount; }

    public int getResumeWordCount() { return resumeWordCount; }
    public void setResumeWordCount(int resumeWordCount) { this.resumeWordCount = resumeWordCount; }

    public String getResumeSnippet() { return resumeSnippet; }
    public void setResumeSnippet(String resumeSnippet) { this.resumeSnippet = resumeSnippet; }

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public Map<String, Integer> getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(Map<String, Integer> scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }

    public List<String> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }

    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }

    public List<String> getAdditionalSkills() { return additionalSkills; }
    public void setAdditionalSkills(List<String> additionalSkills) { this.additionalSkills = additionalSkills; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

    public Map<String, String> getSkillEvidence() { return skillEvidence; }
    public void setSkillEvidence(Map<String, String> skillEvidence) { this.skillEvidence = skillEvidence; }

    public boolean isSkillsClassified() { return skillsClassified; }
    public void setSkillsClassified(boolean skillsClassified) { this.skillsClassified = skillsClassified; }

    public List<String> getRequiredMatchedSkills() { return requiredMatchedSkills; }
    public void setRequiredMatchedSkills(List<String> requiredMatchedSkills) { this.requiredMatchedSkills = requiredMatchedSkills; }

    public List<String> getMissingRequiredSkills() { return missingRequiredSkills; }
    public void setMissingRequiredSkills(List<String> missingRequiredSkills) { this.missingRequiredSkills = missingRequiredSkills; }

    public List<String> getPreferredMatchedSkills() { return preferredMatchedSkills; }
    public void setPreferredMatchedSkills(List<String> preferredMatchedSkills) { this.preferredMatchedSkills = preferredMatchedSkills; }

    public List<String> getMissingPreferredSkills() { return missingPreferredSkills; }
    public void setMissingPreferredSkills(List<String> missingPreferredSkills) { this.missingPreferredSkills = missingPreferredSkills; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
