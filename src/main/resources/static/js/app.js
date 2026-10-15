/**
 * AI Resume Analyzer & Job Matcher - Frontend Application Logic
 * Dynamic Document Upload, Text Extraction, and Algorithmic Job-Matching
 */

document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements - Status & Navigation
    const backendStatusBadge = document.getElementById('backendStatusBadge');
    const statusDot = document.getElementById('statusDot');
    const backendStatusText = document.getElementById('backendStatusText');

    // DOM Elements - Upload Zone
    const dropzone = document.getElementById('dropzone');
    const dropzonePrompt = document.getElementById('dropzonePrompt');
    const resumeFileInput = document.getElementById('resumeFileInput');
    const selectedFileInfo = document.getElementById('selectedFileInfo');
    const fileNameDisplay = document.getElementById('fileNameDisplay');
    const fileSizeDisplay = document.getElementById('fileSizeDisplay');
    const removeFileBtn = document.getElementById('removeFileBtn');
    const fileUploadError = document.getElementById('fileUploadError');

    // DOM Elements - Job Description Form
    const jobTitleInput = document.getElementById('jobTitleInput');
    const companyNameInput = document.getElementById('companyNameInput');
    const jobDescriptionInput = document.getElementById('jobDescriptionInput');
    const loadSampleJobBtn = document.getElementById('loadSampleJobBtn');
    const charCount = document.getElementById('charCount');

    // DOM Elements - Actions & Alerts
    const analyzeBtn = document.getElementById('analyzeBtn');
    const analyzeSpinner = document.getElementById('analyzeSpinner');
    const analyzeBtnText = document.getElementById('analyzeBtnText');
    const resetBtn = document.getElementById('resetBtn');
    const analysisErrorAlert = document.getElementById('analysisErrorAlert');
    const analysisLoadingAlert = document.getElementById('analysisLoadingAlert');
    const resultsSection = document.getElementById('resultsSection');
    const resultsStatusTag = document.getElementById('resultsStatusTag');

    // DOM Elements - Extraction Proof Banner
    const extractionBanner = document.getElementById('extractionBanner');
    const analyzedFileName = document.getElementById('analyzedFileName');
    const extractedStatsDisplay = document.getElementById('extractedStatsDisplay');
    const extractedSnippetDisplay = document.getElementById('extractedSnippetDisplay');

    // DOM Elements - Score & Breakdown
    const matchScoreDisplay = document.getElementById('matchScoreDisplay');
    const scoreRatingBadge = document.getElementById('scoreRatingBadge');
    const scoreCircle = document.getElementById('scoreCircle');

    const skillsMatchVal = document.getElementById('skillsMatchVal');
    const skillsMatchBar = document.getElementById('skillsMatchBar');
    const expMatchVal = document.getElementById('expMatchVal');
    const expMatchBar = document.getElementById('expMatchBar');
    const eduMatchVal = document.getElementById('eduMatchVal');
    const eduMatchBar = document.getElementById('eduMatchBar');
    const keywordMatchVal = document.getElementById('keywordMatchVal');
    const keywordMatchBar = document.getElementById('keywordMatchBar');

    // DOM Elements - Skills Clouds & Recommendations
    const matchedSkillsCloud = document.getElementById('matchedSkillsCloud');
    const missingSkillsCloud = document.getElementById('missingSkillsCloud');
    const suggestionsList = document.getElementById('suggestionsList');

    let currentFile = null;
    let isAnalyzing = false;

    // =========================================================================
    // 1. Backend & MySQL Health Check
    // =========================================================================
    async function checkBackendHealth() {
        try {
            const response = await fetch('/api/health');
            if (response.ok) {
                const data = await response.json();
                statusDot.className = 'status-dot online';
                const isDbConnected = data.database === 'CONNECTED';
                const dbStatusSuffix = isDbConnected ? ' & MySQL Connected' : '';
                backendStatusText.textContent = `Backend${dbStatusSuffix} (v${data.version || '1.0.0'})`;
                backendStatusBadge.title = `Backend: ${data.status} | Database: ${data.database || 'N/A'}`;
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            statusDot.className = 'status-dot offline';
            backendStatusText.textContent = 'Backend Offline';
            backendStatusBadge.title = 'Make sure Spring Boot is running on port 8080';
            console.warn('Health check failed:', error);
        }
    }

    checkBackendHealth();
    setInterval(checkBackendHealth, 30000);

    // =========================================================================
    // 2. Drag & Drop and File Selection
    // =========================================================================
    dropzone.addEventListener('click', (e) => {
        if (e.target !== removeFileBtn && !removeFileBtn.contains(e.target)) {
            resumeFileInput.click();
        }
    });

    // Keyboard accessibility for drag & drop zone
    dropzone.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            if (e.target !== removeFileBtn && !removeFileBtn.contains(e.target)) {
                resumeFileInput.click();
            }
        }
    });

    ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add('drag-active');
        });
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove('drag-active');
        });
    });

    dropzone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files && files.length > 0) {
            handleSelectedFile(files[0]);
        }
    });

    resumeFileInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files.length > 0) {
            handleSelectedFile(e.target.files[0]);
        }
    });

    function handleSelectedFile(file) {
        clearFileError();
        clearAnalysisError();
        clearAnalysisLoading();
        clearAnalysisResults('Awaiting Resume', 'section-tag'); // Clear any previous analysis when a new file is chosen!

        const validExtensions = ['.pdf', '.docx'];
        const fileName = file.name.toLowerCase();
        const isValidExtension = validExtensions.some(ext => fileName.endsWith(ext));

        if (!isValidExtension) {
            showFileError('Invalid file format. Please upload a PDF or DOCX resume.');
            return;
        }

        if (file.size === 0) {
            showFileError('The selected file is empty (0 bytes). Please upload a valid document.');
            return;
        }

        const maxSizeBytes = 5 * 1024 * 1024;
        if (file.size > maxSizeBytes) {
            showFileError('File size exceeds the 5MB limit. Please upload a smaller document.');
            return;
        }

        currentFile = file;
        fileNameDisplay.textContent = file.name;
        fileSizeDisplay.textContent = formatBytes(file.size);

        dropzonePrompt.style.display = 'none';
        selectedFileInfo.style.display = 'flex';
    }

    removeFileBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        clearFileSelection();
    });

    function clearFileSelection() {
        currentFile = null;
        resumeFileInput.value = '';
        selectedFileInfo.style.display = 'none';
        dropzonePrompt.style.display = 'block';
        clearFileError();
        clearAnalysisError();
        clearAnalysisLoading();
        clearAnalysisResults('Awaiting Resume', 'section-tag');
    }

    function showFileError(message) {
        fileUploadError.textContent = message;
        fileUploadError.style.display = 'block';
    }

    function clearFileError() {
        fileUploadError.textContent = '';
        fileUploadError.style.display = 'none';
    }

    function showAnalysisLoading() {
        if (analysisLoadingAlert) {
            analysisLoadingAlert.style.display = 'block';
        }
    }

    function clearAnalysisLoading() {
        if (analysisLoadingAlert) {
            analysisLoadingAlert.style.display = 'none';
        }
    }

    function showAnalysisError(message) {
        analysisErrorAlert.textContent = message;
        analysisErrorAlert.style.display = 'block';
        analysisErrorAlert.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function clearAnalysisError() {
        analysisErrorAlert.textContent = '';
        analysisErrorAlert.style.display = 'none';
    }

    function formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    // =========================================================================
    // 3. Clear Stale Analysis Results
    // =========================================================================
    function clearAnalysisResults(statusText = 'Awaiting Resume', statusClass = 'section-tag') {
        resultsStatusTag.textContent = statusText;
        resultsStatusTag.className = statusClass;
        extractionBanner.style.display = 'none';
        analyzedFileName.textContent = 'None';
        extractedStatsDisplay.textContent = '0 words';
        extractedSnippetDisplay.textContent = '';

        matchScoreDisplay.textContent = '0';
        scoreRatingBadge.textContent = 'Ready';
        scoreRatingBadge.className = 'score-badge';
        scoreRatingBadge.style.backgroundColor = '';
        scoreRatingBadge.style.color = '';
        scoreCircle.style.background = 'conic-gradient(#cbd5e1 0deg 360deg)';

        skillsMatchVal.textContent = '0%';
        skillsMatchBar.style.width = '0%';
        expMatchVal.textContent = '0%';
        expMatchBar.style.width = '0%';
        eduMatchVal.textContent = '0%';
        eduMatchBar.style.width = '0%';
        keywordMatchVal.textContent = '0%';
        keywordMatchBar.style.width = '0%';

        matchedSkillsCloud.innerHTML = '<span class="empty-cloud-text">Upload a resume and click Analyze to view matched skills.</span>';
        missingSkillsCloud.innerHTML = '<span class="empty-cloud-text">Missing skills will appear here after analysis.</span>';
        suggestionsList.innerHTML = '<li class="suggestion-item"><span class="suggestion-bullet">&#x2022;</span><div>Upload your resume and click Analyze to generate personalized recommendations.</div></li>';
    }

    // =========================================================================
    // 4. Job Description Input & Sample Loader
    // =========================================================================
    const MIN_JOB_DESC_CHARS = 15;
    const MAX_JOB_DESC_CHARS = 20000;

    jobDescriptionInput.addEventListener('input', () => {
        const text = jobDescriptionInput.value;
        const length = text.length;
        const words = text.trim() ? text.trim().split(/\s+/).length : 0;

        if (length > MAX_JOB_DESC_CHARS) {
            charCount.textContent = `${length.toLocaleString()} / 20,000 characters (${words} words) - Exceeds limit`;
            charCount.style.color = '#ef4444';
            charCount.style.fontWeight = '600';
        } else {
            charCount.textContent = `${length.toLocaleString()} characters (${words} words)`;
            charCount.style.color = '';
            charCount.style.fontWeight = '';
        }
        clearAnalysisError();
    });

    loadSampleJobBtn.addEventListener('click', () => {
        jobTitleInput.value = 'Java Full Stack Developer';
        companyNameInput.value = 'Innovate Solutions Inc.';
        jobDescriptionInput.value = `We are seeking an ambitious Java Full Stack Developer with 1-3 years of experience.
        
Key Responsibilities:
- Design, build, and maintain efficient, reusable, and reliable Java and Spring Boot microservices.
- Develop responsive, user-friendly frontend interfaces using modern HTML5, CSS3, and JavaScript.
- Write robust RESTful APIs, interact with relational databases (MySQL/PostgreSQL), and optimize SQL queries.
- Collaborate with cross-functional teams to integrate cloud services (AWS EC2, S3) and Docker containers.
- Write clean unit and integration tests using JUnit and Mockito.

Requirements:
- Strong core Java (Java 11 or 17), Spring Boot, Spring Data JPA, and RESTful web services.
- Solid experience with MySQL schema design and CRUD operations.
- Familiarity with Git, Maven, and modern frontend fundamentals (HTML, CSS, JS).
- Exposure to containerization (Docker, Kubernetes) and CI/CD pipelines is a strong plus.`;
        
        jobDescriptionInput.dispatchEvent(new Event('input'));
    });

    // =========================================================================
    // 5. Dynamic Resume Analysis Call (POST /api/analyze)
    // =========================================================================
    analyzeBtn.addEventListener('click', async () => {
        // Prevent accidental duplicate clicks/submissions while analysis is in progress
        if (isAnalyzing) {
            return;
        }

        clearAnalysisError();

        // 1. Validation checks
        if (!currentFile) {
            showAnalysisError('Please select or upload a resume file (.pdf or .docx) before analyzing.');
            dropzone.scrollIntoView({ behavior: 'smooth' });
            return;
        }

        const rawJobDesc = jobDescriptionInput.value;
        const trimmedJobDesc = rawJobDesc ? rawJobDesc.trim() : '';

        if (!trimmedJobDesc) {
            showAnalysisError('Please enter a target job description. The job description cannot be empty or whitespace only.');
            jobDescriptionInput.focus();
            return;
        }

        if (trimmedJobDesc.length < MIN_JOB_DESC_CHARS) {
            showAnalysisError('Job description is too short. Please provide a meaningful job description (at least 15 characters).');
            jobDescriptionInput.focus();
            return;
        }

        if (trimmedJobDesc.length > MAX_JOB_DESC_CHARS) {
            showAnalysisError(`Job description exceeds the maximum allowed length of ${MAX_JOB_DESC_CHARS.toLocaleString()} characters. Please provide a more concise job posting.`);
            jobDescriptionInput.focus();
            return;
        }

        // 2. Prepare FormData to send the newly selected file
        const formData = new FormData();
        formData.append('file', currentFile);
        formData.append('jobDescription', trimmedJobDesc);
        if (jobTitleInput.value.trim()) {
            formData.append('jobTitle', jobTitleInput.value.trim());
        }
        if (companyNameInput.value.trim()) {
            formData.append('company', companyNameInput.value.trim());
        }

        // 3. UI Loading & Duplicate Prevention State
        isAnalyzing = true;
        analyzeBtn.disabled = true;
        analyzeSpinner.style.display = 'inline-block';
        analyzeBtnText.textContent = 'Parsing & Analyzing Resume...';
        showAnalysisLoading();

        // Ensure previous results are cleared so they cannot be confused with the in-flight request
        clearAnalysisResults('Analyzing in memory...', 'section-tag analyzing');

        try {
            const response = await fetch('/api/analyze', {
                method: 'POST',
                body: formData
            });

            let data;
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                data = await response.json().catch(() => null);
            }

            if (!response.ok) {
                let errorMsg;
                if (data && data.message) {
                    errorMsg = data.message;
                } else if (response.status === 413) {
                    errorMsg = 'Uploaded file exceeds the maximum size limit of 5MB. Please upload a smaller resume document.';
                } else if (response.status >= 500) {
                    errorMsg = `Server encountered an internal error (HTTP ${response.status}). Please check your file format and try again.`;
                } else {
                    errorMsg = `Request failed (HTTP ${response.status}). Please check your file and job description and try again.`;
                }
                throw new Error(errorMsg);
            }

            if (!data) {
                throw new Error('Received an empty response from the server. Please check your connection and retry.');
            }

            // 4. Render Dynamic Results
            clearAnalysisLoading();
            renderAnalysisResults(data);

            // Scroll to results dashboard
            resultsSection.scrollIntoView({ behavior: 'smooth' });

        } catch (error) {
            console.error('Analysis failed:', error);
            clearAnalysisLoading();

            let friendlyMessage = error.message;
            if (!friendlyMessage || friendlyMessage === 'Failed to fetch' || error.name === 'TypeError') {
                friendlyMessage = 'Unable to connect to the analysis server. Please check your network connection and verify that the backend application is running, then try again.';
            }

            showAnalysisError(friendlyMessage);
            // Reset results to ensure stale results are not shown as if they belong to the failed request
            clearAnalysisResults('Analysis Failed', 'section-tag failed');

        } finally {
            clearAnalysisLoading();
            isAnalyzing = false;
            analyzeSpinner.style.display = 'none';
            analyzeBtnText.innerHTML = '&#x2728; Analyze Resume &amp; Match Job';
            analyzeBtn.disabled = false;
        }
    });

    // =========================================================================
    // 6. Dynamic Result Rendering
    // =========================================================================
    function renderAnalysisResults(data) {
        resultsStatusTag.textContent = 'Analysis Complete';

        // A. Extraction Proof Banner
        extractionBanner.style.display = 'block';
        analyzedFileName.textContent = data.fileName || currentFile.name;
        extractedStatsDisplay.textContent = `${data.resumeWordCount} words (${data.resumeCharCount} characters)`;
        extractedSnippetDisplay.textContent = `"${data.resumeSnippet || ''}"`;

        // B. Overall Score & Rating
        const score = data.matchScore || 0;
        matchScoreDisplay.textContent = score;
        scoreRatingBadge.textContent = data.rating || 'Match Computed';

        // Dynamic Badge Styling
        if (score >= 80) {
            scoreRatingBadge.className = 'score-badge strong';
        } else if (score >= 60) {
            scoreRatingBadge.className = 'score-badge';
            scoreRatingBadge.style.backgroundColor = '#dbeafe';
            scoreRatingBadge.style.color = '#1e40af';
        } else {
            scoreRatingBadge.className = 'score-badge';
            scoreRatingBadge.style.backgroundColor = '#fef3c7';
            scoreRatingBadge.style.color = '#92400e';
        }

        // Radial Conic Gauge
        const deg = Math.round((score / 100) * 360);
        const gaugeColor = score >= 80 ? 'var(--success)' : (score >= 60 ? 'var(--info)' : 'var(--warning)');
        scoreCircle.style.background = `conic-gradient(${gaugeColor} 0deg ${deg}deg, #e2e8f0 ${deg}deg 360deg)`;

        // C. Sub-metrics Breakdown
        const breakdown = data.scoreBreakdown || {};
        const skillsScore = breakdown['Required Skills Match'] || 0;
        const expScore = breakdown['Experience Alignment'] || 0;
        const eduScore = breakdown['Education & Qualifications'] || 0;
        const kwScore = breakdown['Job Keyword Alignment'] || 0;

        skillsMatchVal.textContent = `${skillsScore}%`;
        skillsMatchBar.style.width = `${skillsScore}%`;

        expMatchVal.textContent = `${expScore}%`;
        expMatchBar.style.width = `${expScore}%`;

        eduMatchVal.textContent = `${eduScore}%`;
        eduMatchBar.style.width = `${eduScore}%`;

        keywordMatchVal.textContent = `${kwScore}%`;
        keywordMatchBar.style.width = `${kwScore}%`;

        // D. Render Matched Skills with Evidence Snippets (+ Required/Preferred badge when classified)
        matchedSkillsCloud.innerHTML = '';
        if (data.matchedSkills && data.matchedSkills.length > 0) {
            data.matchedSkills.forEach(skill => {
                const wrapper = document.createElement('div');
                wrapper.className = 'skill-evidence-wrapper';

                const tag = document.createElement('span');
                tag.className = 'tag tag-matched';
                tag.textContent = skill;

                // Add [Required] / [Preferred] badge when the JD was classified
                if (data.skillsClassified) {
                    const isPreferred = data.preferredMatchedSkills && data.preferredMatchedSkills.includes(skill);
                    const badge = document.createElement('span');
                    badge.className = isPreferred ? 'skill-tier-badge preferred' : 'skill-tier-badge required';
                    badge.textContent  = isPreferred ? 'Preferred' : 'Required';
                    tag.appendChild(badge);
                }

                // Evidence is taken only from the actual uploaded resume text
                const snippet = (data.skillEvidence && data.skillEvidence[skill])
                    ? data.skillEvidence[skill]
                    : 'Evidence not found';

                const evidenceEl = document.createElement('div');
                evidenceEl.className = 'skill-evidence-text';
                evidenceEl.title = snippet;
                evidenceEl.textContent = snippet;

                wrapper.appendChild(tag);
                wrapper.appendChild(evidenceEl);
                matchedSkillsCloud.appendChild(wrapper);
            });
        } else {
            matchedSkillsCloud.innerHTML = '<span class="empty-cloud-text">No direct skills matched the requirements in this job description.</span>';
        }

        // E. Render Missing Skills (classified into Required / Preferred when possible)
        missingSkillsCloud.innerHTML = '';
        if (data.missingSkills && data.missingSkills.length > 0) {
            if (data.skillsClassified && (data.missingRequiredSkills?.length > 0 || data.missingPreferredSkills?.length > 0)) {
                // --- Classified view ---
                if (data.missingRequiredSkills && data.missingRequiredSkills.length > 0) {
                    const reqLabel = document.createElement('div');
                    reqLabel.className = 'missing-section-label required';
                    reqLabel.textContent = '🔴 Missing Required Skills';
                    missingSkillsCloud.appendChild(reqLabel);
                    data.missingRequiredSkills.forEach(skill => {
                        const tag = document.createElement('span');
                        tag.className = 'tag tag-missing tag-missing-required';
                        tag.textContent = skill;
                        missingSkillsCloud.appendChild(tag);
                    });
                }
                if (data.missingPreferredSkills && data.missingPreferredSkills.length > 0) {
                    const prefLabel = document.createElement('div');
                    prefLabel.className = 'missing-section-label preferred';
                    prefLabel.textContent = '🟡 Missing Preferred Skills (Nice-to-Have)';
                    missingSkillsCloud.appendChild(prefLabel);
                    data.missingPreferredSkills.forEach(skill => {
                        const tag = document.createElement('span');
                        tag.className = 'tag tag-missing tag-missing-preferred';
                        tag.textContent = skill;
                        missingSkillsCloud.appendChild(tag);
                    });
                }
            } else {
                // --- Flat fallback for ambiguous JDs ---
                data.missingSkills.forEach(skill => {
                    const tag = document.createElement('span');
                    tag.className = 'tag tag-missing';
                    tag.textContent = skill;
                    missingSkillsCloud.appendChild(tag);
                });
            }
        } else {
            missingSkillsCloud.innerHTML = '<span class="tag tag-matched">&#x2714; Excellent! All required skills were found in your resume.</span>';
        }

        // F. Render Recommendations
        suggestionsList.innerHTML = '';
        if (data.recommendations && data.recommendations.length > 0) {
            data.recommendations.forEach(rec => {
                const li = document.createElement('li');
                li.className = 'suggestion-item';
                li.innerHTML = `<span class="suggestion-bullet">&#x2022;</span><div>${rec}</div>`;
                suggestionsList.appendChild(li);
            });
        } else {
            suggestionsList.innerHTML = '<li class="suggestion-item"><span class="suggestion-bullet">&#x2022;</span><div>Your resume has strong alignment with this job posting.</div></li>';
        }
    }

    // =========================================================================
    // 7. Reset Button Handler
    // =========================================================================
    resetBtn.addEventListener('click', () => {
        if (isAnalyzing) return; // Prevent reset while analysis is running
        clearFileSelection();
        jobTitleInput.value = '';
        companyNameInput.value = '';
        jobDescriptionInput.value = '';
        charCount.textContent = '0 characters (0 words)';
        charCount.style.color = '';
        charCount.style.fontWeight = '';
        clearAnalysisError();
        clearAnalysisLoading();
        clearAnalysisResults('Awaiting Resume', 'section-tag');
    });
});
