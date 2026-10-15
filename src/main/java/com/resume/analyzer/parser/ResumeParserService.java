package com.resume.analyzer.parser;

import com.resume.analyzer.exception.InvalidResumeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * In-memory document parsing service for resumes.
 * Extracts clean plain text from PDF and DOCX files without persisting
 * any binary files or text to disk or database.
 */
@Service
public class ResumeParserService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    /**
     * Extracts text content from an uploaded resume file.
     *
     * @param file the uploaded MultipartFile (PDF or DOCX)
     * @return clean plain text extracted from the document
     * @throws InvalidResumeException if file is invalid, empty, or unreadable
     */
    public String extractText(MultipartFile file) {
        validateFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            log.error("Failed to read file bytes: {}", e.getMessage());
            throw new InvalidResumeException("Unable to read uploaded file data. Please try again.", e);
        }

        if (bytes == null || bytes.length == 0) {
            throw new InvalidResumeException("The uploaded resume file is empty (0 bytes).");
        }

        String originalFilename = file.getOriginalFilename();
        String lowerFilename = originalFilename != null ? originalFilename.trim().toLowerCase() : "";

        String extractedText;
        if (lowerFilename.endsWith(".pdf")) {
            extractedText = extractFromPdf(bytes, originalFilename);
        } else if (lowerFilename.endsWith(".docx")) {
            extractedText = extractFromDocx(bytes, originalFilename);
        } else {
            throw new InvalidResumeException("Unsupported file type. Please upload a valid .pdf or .docx resume document.");
        }

        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new InvalidResumeException(
                    "No readable text could be extracted from '" + originalFilename + "'. " +
                    "If this is a scanned document (image only), please use a document with selectable text."
            );
        }

        log.info("Successfully extracted {} characters from resume: {}", extractedText.length(), originalFilename);
        return extractedText.trim();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidResumeException("No resume file was uploaded or the uploaded file is empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidResumeException("File size exceeds the 5MB limit. Please upload a smaller resume document.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidResumeException("The uploaded file does not have a valid filename. Please upload a .pdf or .docx file.");
        }

        String lowerFilename = originalFilename.trim().toLowerCase();
        if (!lowerFilename.endsWith(".pdf") && !lowerFilename.endsWith(".docx")) {
            throw new InvalidResumeException("Unsupported file format. Please upload a .pdf or .docx resume document.");
        }
    }

    private boolean hasPdfSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        // Verify %PDF (0x25, 0x50, 0x44, 0x46) within the first 1024 bytes
        int maxScan = Math.min(bytes.length - 4, 1024);
        for (int i = 0; i <= maxScan; i++) {
            if (bytes[i] == 0x25 && bytes[i + 1] == 0x50 && bytes[i + 2] == 0x44 && bytes[i + 3] == 0x46) {
                return true;
            }
        }
        return false;
    }

    private boolean hasZipSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        // Standard ZIP local file header PK\x03\x04
        return bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04;
    }

    private String extractFromPdf(byte[] bytes, String filename) {
        if (!hasPdfSignature(bytes)) {
            throw new InvalidResumeException("Invalid or corrupted PDF file. The uploaded document does not contain a valid PDF structure.");
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new InvalidResumeException("Password-protected PDF files cannot be analyzed. Please upload an unprotected document.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidResumeException ire) {
            throw ire;
        } catch (Exception e) {
            log.error("Failed to parse PDF document '{}': {}", filename, e.getMessage());
            throw new InvalidResumeException("Unable to read PDF document: The file appears to be damaged or corrupted.", e);
        }
    }

    private String extractFromDocx(byte[] bytes, String filename) {
        if (!hasZipSignature(bytes)) {
            throw new InvalidResumeException("Invalid or corrupted DOCX file. The uploaded document is not a valid Word (.docx) file.");
        }

        try (InputStream is = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (InvalidResumeException ire) {
            throw ire;
        } catch (Exception e) {
            log.error("Failed to parse DOCX document '{}': {}", filename, e.getMessage());
            throw new InvalidResumeException("Unable to read DOCX document: The file appears to be damaged or corrupted.", e);
        }
    }
}
